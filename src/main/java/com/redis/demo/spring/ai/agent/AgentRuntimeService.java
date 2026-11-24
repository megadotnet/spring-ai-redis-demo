package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.memory.ConversationMemory;
import com.redis.demo.spring.ai.model.AgentChatRequest;
import com.redis.demo.spring.ai.model.AgentChatResponse;
import com.redis.demo.spring.ai.model.Evidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 有界 Agent Runtime：应用代码控制流程，LLM 只负责生成。
 * 控制流：读取会话历史 → 规则规划 → 至多 MAX_TOOL_CALLS 次检索 →
 * 上下文组装（预算/去重）→ 答案生成 → 引用校验 → 保存会话记录。
 *
 * 硬限制：最大检索次数、topK 上下界、上下文长度；证据不足时稳定拒答，
 * 不依赖 prompt 要求模型“不要循环”。
 */
@Service
public class AgentRuntimeService {

    private static final Logger logger = LoggerFactory.getLogger(AgentRuntimeService.class);

    private static final int MAX_TOOL_CALLS = 3;
    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 20;
    private static final int MAX_CONTEXT_CHARS = 12_000;

    private final QueryPlanner planner;
    private final RetrievalTool retrievalTool;
    private final ContextBuilder contextBuilder;
    private final AnswerGenerator answerGenerator;
    private final GroundingChecker groundingChecker;
    private final ConversationMemory memory;

    public AgentRuntimeService(
            QueryPlanner planner,
            RetrievalTool retrievalTool,
            ContextBuilder contextBuilder,
            AnswerGenerator answerGenerator,
            GroundingChecker groundingChecker,
            ConversationMemory memory) {
        this.planner = planner;
        this.retrievalTool = retrievalTool;
        this.contextBuilder = contextBuilder;
        this.answerGenerator = answerGenerator;
        this.groundingChecker = groundingChecker;
        this.memory = memory;
    }

    public AgentChatResponse run(AgentChatRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("question is required");
        }

        String traceId = UUID.randomUUID().toString();
        String sessionId = request.sessionId();

        // MVP 中读取历史，供日志、后续问题改写或生成时使用。
        // 此版本暂不将历史直接拼入 prompt，避免未经筛选的历史污染证据；
        // 需明确历史摘要、冲突处理和上下文预算后再引入 ConversationContextBuilder。
        List<ConversationMemory.Turn> history =
                sessionId == null ? List.of() : memory.recent(sessionId);
        logger.info("Agent run traceId={} session={} historyTurns={}",
                traceId, sessionId, history.size());

        QueryPlanner.QueryPlan plan = planner.plan(request.question());
        if (plan.needsClarification()) {
            return new AgentChatResponse(
                    traceId,
                    plan.clarificationQuestion(),
                    List.of(),
                    List.of("clarify"),
                    false
            );
        }

        int topK = request.topK() == null
                ? DEFAULT_TOP_K
                : Math.max(1, Math.min(request.topK(), MAX_TOP_K));

        List<String> steps = new ArrayList<>();
        List<Evidence> allEvidence = new ArrayList<>();

        List<String> queries = plan.retrievalQueries().stream()
                .limit(MAX_TOOL_CALLS)
                .toList();

        for (String query : queries) {
            steps.add("hybrid_retrieve");
            try {
                allEvidence.addAll(retrievalTool.retrieve(query, topK));
            } catch (Exception ex) {
                // 工具异常不使整条链失败：记录结构化失败步骤并继续按证据情况降级。
                logger.error("Retrieval tool failed for query '{}': {}", query, ex.getMessage(), ex);
                steps.add("retrieval_failed");
            }
        }

        ContextBuilder.Context context =
                contextBuilder.build(allEvidence, MAX_CONTEXT_CHARS);

        if (context.evidence().isEmpty()) {
            String noEvidence = "当前知识库中未检索到足够的相关资料，无法可靠回答。";
            saveTurns(sessionId, request.question(), noEvidence);
            return new AgentChatResponse(
                    traceId,
                    noEvidence,
                    List.of(),
                    List.copyOf(steps),
                    false
            );
        }

        steps.add("generate_answer");
        String answer = answerGenerator.answer(request.question(), context.text());

        boolean grounded = groundingChecker.hasValidCitations(
                answer,
                context.evidence()
        );

        // 不通过时先返回透明降级结果。下一迭代再加入一次有限修正/重检索。
        if (!grounded) {
            answer = "检索到相关资料，但当前生成结果未通过引用校验。请尝试重新提问，或查看检索证据。";
            steps.add("grounding_check_failed");
        } else {
            steps.add("grounding_check_passed");
        }

        saveTurns(sessionId, request.question(), answer);

        return new AgentChatResponse(
                traceId,
                answer,
                context.evidence(),
                List.copyOf(steps),
                grounded
        );
    }

    private void saveTurns(String sessionId, String question, String answer) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            memory.append(sessionId, "user", question);
            memory.append(sessionId, "assistant", answer);
        } catch (Exception ex) {
            // 记忆写入失败不应阻塞用户响应；记录后由监控发现。
            logger.warn("Failed to persist conversation turns for session {}: {}",
                    sessionId, ex.getMessage());
        }
    }
}
