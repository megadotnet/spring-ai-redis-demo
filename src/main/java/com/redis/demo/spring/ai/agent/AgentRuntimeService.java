package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.memory.AgentTaskStore;
import com.redis.demo.spring.ai.memory.ConversationMemory;
import com.redis.demo.spring.ai.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Stage 2 有界 Agent Runtime：多步、有界状态流。
 *
 * 控制流：
 * PLAN → INITIAL_RETRIEVAL → CONTEXT_BUILD → GENERATE → GROUNDING_CHECK
 *   ├─ 通过 → COMPLETE
 *   └─ 失败 → SUPPLEMENTAL_RETRIEVAL（最多 1 次）→ REBUILD_CONTEXT
 *            → REPAIR（最多 1 次）→ GROUNDING_CHECK
 *                 ├─ 通过 → COMPLETE_REPAIRED
 *                 └─ 失败 → ABSTAIN
 *
 * 设计约束：一次请求最多执行一次计划生成、一次补充检索和一次答案修正。
 */
@Service
public class AgentRuntimeService {

    private static final Logger logger = LoggerFactory.getLogger(AgentRuntimeService.class);

    private final AgentPlanner planner;
    private final QueryRewriteService rewriteService;
    private final RetrievalTool retrievalTool;
    private final ContextBuilder contextBuilder;
    private final AnswerGenerator answerGenerator;
    private final CitationValidator citationValidator;
    private final AnswerRepairService repairService;
    private final AgentTaskStore taskStore;
    private final ConversationMemory conversationMemory;
    private final AgentLimits limits;
    private final AgentTelemetry telemetry;

    public AgentRuntimeService(
            AgentPlanner planner,
            QueryRewriteService rewriteService,
            RetrievalTool retrievalTool,
            ContextBuilder contextBuilder,
            AnswerGenerator answerGenerator,
            CitationValidator citationValidator,
            AnswerRepairService repairService,
            AgentTaskStore taskStore,
            ConversationMemory conversationMemory,
            AgentLimits limits,
            AgentTelemetry telemetry) {
        this.planner = planner;
        this.rewriteService = rewriteService;
        this.retrievalTool = retrievalTool;
        this.contextBuilder = contextBuilder;
        this.answerGenerator = answerGenerator;
        this.citationValidator = citationValidator;
        this.repairService = repairService;
        this.taskStore = taskStore;
        this.conversationMemory = conversationMemory;
        this.limits = limits;
        this.telemetry = telemetry;
    }

    public AgentChatResponse run(AgentChatRequest request) {
        validate(request);

        String traceId = UUID.randomUUID().toString();
        Instant started = Instant.now();
        List<AgentTraceEvent> events = new ArrayList<>();
        List<String> steps = new ArrayList<>();

        try {
            return executeRun(request, traceId, started, events, steps);
        } catch (RuntimeException ex) {
            logger.error("Agent run failed for traceId={}: {}", traceId, ex.getMessage(), ex);
            telemetry.increment("rag.agent.answer.outcome", "error");
            finish(traceId, request.sessionId(), started, events, "ERROR");
            throw ex;
        }
    }

    private AgentChatResponse executeRun(
            AgentChatRequest request,
            String traceId,
            Instant started,
            List<AgentTraceEvent> events,
            List<String> steps) {

        saveState(traceId, request.sessionId(), "PLANNING",
                started, events, null);

        // === 阶段 1：规划 ===
        AgentPlan plan = timed("plan", events, () -> planner.plan(request.question()));
        steps.add("plan");

        List<String> initialQueries = plan.subQuestions().stream()
                .flatMap(sq -> sq.searchQueries().stream())
                .filter(q -> q != null && !q.isBlank())
                .distinct()
                .limit(limits.maxInitialQueries())
                .toList();

        int topK = clampTopK(request.topK());
        List<Evidence> evidence = new ArrayList<>();

        // === 阶段 2：初次检索 ===
        saveState(traceId, request.sessionId(), "INITIAL_RETRIEVAL",
                started, events, null);

        int toolCalls = 0;
        for (String query : initialQueries) {
            if (toolCalls >= limits.maxToolCalls()) {
                telemetry.increment("rag.agent.budget.exceeded", "tool_calls");
                break;
            }
            evidence.addAll(retrieveOne(query, topK, events));
            toolCalls++;
        }

        evidence = deduplicate(evidence);
        steps.add("initial_retrieval");

        // === 无证据直接拒答 ===
        if (evidence.isEmpty()) {
            String answer = "当前知识库未检索到足够证据，无法可靠回答。";
            saveConversation(request.sessionId(), request.question(), answer);
            finish(traceId, request.sessionId(), started, events, "ABSTAINED");
            telemetry.increment("rag.agent.answer.outcome", "abstained");
            return response(traceId, answer, List.of(), steps,
                    "ABSTAINED", false);
        }

        // === 阶段 3：上下文组装 ===
        ContextBuilder.Context context =
                contextBuilder.build(evidence, limits.maxContextCharacters());
        steps.add("context_build");

        // === 阶段 4：答案生成 ===
        saveState(traceId, request.sessionId(), "GENERATING",
                started, events, null);

        String answer = timed("generate", events, () ->
                answerGenerator.answer(request.question(), context.text()));
        steps.add("generate");

        // === 阶段 5：引用校验 ===
        GroundingResult check = timed("grounding", events, () ->
                citationValidator.validate(answer, context.evidence()));
        steps.add("grounding_check");

        // 用于跟踪当前可变状态
        String currentAnswer = answer;
        GroundingResult currentCheck = check;
        ContextBuilder.Context currentContext = context;
        List<Evidence> currentEvidence = evidence;

        // === 阶段 6：补充检索（最多 1 次，仅在校验失败时） ===
        if (!currentCheck.grounded()
                && limits.maxSupplementalQueries() > 0
                && toolCalls < limits.maxToolCalls()) {

            steps.add("rewrite_once");
            saveState(traceId, request.sessionId(), "SUPPLEMENTAL_RETRIEVAL",
                    started, events, null);

            final ContextBuilder.Context rewriteContext = currentContext;
            final String rewriteFeedback = currentCheck.feedback();

            List<String> followUpQueries = timed("rewrite", events, () ->
                    rewriteService.rewrite(
                            request.question(),
                            rewriteContext.evidence(),
                            rewriteFeedback
                    )).stream()
                    .distinct()
                    .limit(limits.maxSupplementalQueries())
                    .toList();

            List<Evidence> supplementalEvidence = new ArrayList<>(currentEvidence);
            for (String query : followUpQueries) {
                if (toolCalls >= limits.maxToolCalls()) {
                    telemetry.increment("rag.agent.budget.exceeded", "tool_calls");
                    break;
                }
                supplementalEvidence.addAll(retrieveOne(query, topK, events));
                toolCalls++;
            }

            currentEvidence = deduplicate(supplementalEvidence);
            currentContext = contextBuilder.build(
                    currentEvidence,
                    limits.maxContextCharacters()
            );
            steps.add("supplemental_retrieval");
        }

        // === 阶段 7：修正（最多 1 次，仅在校验仍失败时） ===
        if (!currentCheck.grounded() && !currentContext.evidence().isEmpty()) {
            steps.add("repair_once");
            saveState(traceId, request.sessionId(), "REPAIRING",
                    started, events, null);

            final ContextBuilder.Context repairContext = currentContext;
            final String repairFeedback = currentCheck.feedback();

            currentAnswer = timed("repair", events, () ->
                    repairService.repair(
                            request.question(),
                            answer,
                            repairContext.evidence(),
                            repairFeedback
                    ));

            final String repairedAnswer = currentAnswer;
            currentCheck = timed("grounding", events, () ->
                    citationValidator.validate(repairedAnswer, repairContext.evidence()));

            steps.add(currentCheck.grounded()
                    ? "grounding_check_passed_after_repair"
                    : "grounding_check_failed_after_repair");
        }

        // === 最终判定 ===
        if (!currentCheck.grounded()) {
            currentAnswer = "检索到相关资料，但无法确认答案中的结论均有证据支持。为避免误导，本次不提供确定性答案。";
            telemetry.increment("rag.agent.answer.outcome", "abstained");
        } else {
            boolean wasRepaired = steps.contains("repair_once");
            telemetry.increment("rag.agent.answer.outcome",
                    wasRepaired ? "repaired" : "grounded");
        }

        saveConversation(request.sessionId(), request.question(), currentAnswer);

        boolean wasRepaired = steps.contains("repair_once");
        String finalStatus = currentCheck.grounded()
                ? (wasRepaired ? "COMPLETE_REPAIRED" : "COMPLETE")
                : "ABSTAINED";

        finish(traceId, request.sessionId(), started, events, finalStatus);

        return response(
                traceId,
                currentAnswer,
                currentContext.evidence(),
                steps,
                finalStatus,
                currentCheck.grounded()
        );
    }

    private List<Evidence> retrieveOne(
            String query,
            int topK,
            List<AgentTraceEvent> events) {

        long start = System.nanoTime();
        boolean success = false;
        List<Evidence> results = List.of();

        try {
            results = retrievalTool.retrieve(query, topK);
            success = true;
            telemetry.increment("rag.agent.tool.calls", "success");
            return results;
        } catch (Exception ex) {
            logger.error("Retrieval failed for query hash {}: {}",
                    hash(query), ex.getMessage());
            telemetry.increment("rag.agent.tool.calls", "failure");
            return List.of();
        } finally {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            events.add(new AgentTraceEvent(
                    "retrieve",
                    "hybrid_retrieve",
                    hash(query),
                    results.stream()
                            .map(Evidence::documentId)
                            .filter(Objects::nonNull)
                            .toList(),
                    elapsed,
                    success,
                    success ? null : "retrieval_error",
                    Instant.now()
            ));
        }
    }

    private List<Evidence> deduplicate(List<Evidence> evidence) {
        var unique = new LinkedHashMap<String, Evidence>();
        for (Evidence item : evidence) {
            String key = item.documentId() == null
                    ? String.valueOf(
                            item.content() == null ? 0 : item.content().hashCode())
                    : item.documentId();
            unique.putIfAbsent(key, item);
        }
        return unique.values().stream()
                .limit(limits.maxEvidence())
                .toList();
    }

    private int clampTopK(Integer requested) {
        int value = requested == null ? 10 : requested;
        return Math.max(1, Math.min(value, limits.maxTopK()));
    }

    private void validate(AgentChatRequest request) {
        if (request == null || request.question() == null
                || request.question().isBlank()) {
            throw new IllegalArgumentException("question is required");
        }
        if (request.question().length() > 4_000) {
            throw new IllegalArgumentException("question is too long");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            ).substring(0, 16);
        } catch (Exception ex) {
            return "unavailable";
        }
    }

    /**
     * 带事件记录的计时包装。
     */
    private <T> T timed(String stage, List<AgentTraceEvent> events, Callable<T> call) {
        long start = System.nanoTime();
        boolean success = false;
        try {
            T result = telemetry.time(stage, call);
            success = true;
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Agent stage failed: " + stage, ex);
        } finally {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            events.add(new AgentTraceEvent(
                    stage,
                    null,
                    null,
                    List.of(),
                    elapsed,
                    success,
                    success ? null : "stage_error",
                    Instant.now()
            ));
        }
    }

    private void saveConversation(String sessionId, String question, String answer) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            conversationMemory.append(sessionId, "user", question);
            conversationMemory.append(sessionId, "assistant", answer);
        } catch (Exception ex) {
            logger.warn("Failed to persist conversation turns for session {}: {}",
                    sessionId, ex.getMessage());
        }
    }

    private void saveState(
            String traceId,
            String sessionId,
            String status,
            Instant started,
            List<AgentTraceEvent> events,
            String finalStatus) {
        taskStore.save(new AgentTaskState(
                traceId,
                sessionId,
                status,
                started,
                Instant.now(),
                List.copyOf(events),
                finalStatus
        ));
    }

    private void finish(
            String traceId,
            String sessionId,
            Instant started,
            List<AgentTraceEvent> events,
            String finalStatus) {
        saveState(traceId, sessionId, finalStatus,
                started, events, finalStatus);
    }

    private AgentChatResponse response(
            String traceId,
            String answer,
            List<Evidence> evidence,
            List<String> steps,
            String status,
            boolean grounded) {
        return new AgentChatResponse(
                traceId,
                answer,
                evidence,
                steps,
                status,
                grounded
        );
    }
}
