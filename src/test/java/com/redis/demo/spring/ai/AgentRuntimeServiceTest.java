package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.agent.*;
import com.redis.demo.spring.ai.memory.AgentTaskStore;
import com.redis.demo.spring.ai.memory.ConversationMemory;
import com.redis.demo.spring.ai.model.AgentChatRequest;
import com.redis.demo.spring.ai.model.AgentChatResponse;
import com.redis.demo.spring.ai.model.AgentTaskState;
import com.redis.demo.spring.ai.model.Evidence;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Stage 2 AgentRuntimeService 关键路径单元测试：
 * - 问题拆分超过限制：只使用 maxInitialQueries 上限以内的 query；
 * - Planner 返回无效 JSON：降级为原问题单 query；
 * - 初次无检索结果：直接拒答，不执行补检、生成和修正；
 * - 第一次 grounding 检查失败：最多执行一次补充检索和一次修正；
 * - 修正后仍失败：返回 ABSTAINED 拒答状态，不再调用模型；
 * - 检索工具异常：记录工具失败并进入受控降级；
 * - 超过 tool call budget：不执行额外工具；
 * - 同一 document ID 重复出现：证据去重；
 * - Session ID 缺失：不读写会话记忆，仍完成单轮请求；
 * - 文档引用不存在：grounded=false，不返回通过状态。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentRuntimeServiceTest {

    @Mock
    private ChatModel plannerChatModel;

    @Mock
    private QueryRewriteService rewriteService;

    @Mock
    private AnswerGenerator answerGenerator;

    @Mock
    private AnswerRepairService repairService;

    @Mock
    private AgentTaskStore taskStore;

    @Mock
    private ConversationMemory memory;

    private AgentLimits defaultLimits() {
        return new AgentLimits(3, 3, 1, 4, 20, 12, 12_000, Duration.ofSeconds(55));
    }

    private AgentRuntimeService newService(RetrievalTool retrievalTool, AgentLimits limits) {
        return new AgentRuntimeService(
                new AgentPlanner(plannerChatModel),
                rewriteService,
                retrievalTool,
                new ContextBuilder(),
                answerGenerator,
                new CitationValidator(),
                repairService,
                taskStore,
                memory,
                limits,
                new AgentTelemetry(new SimpleMeterRegistry())
        );
    }

    private void mockPlannerJson(String json) {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
        when(plannerChatModel.call(any(Prompt.class))).thenReturn(response);
    }

    private static Evidence evidence(String id, String content) {
        return new Evidence(id, content, "doc.pdf", 1, "hybrid", 0.9);
    }

    @Test
    void shouldReturnAbstainedWithoutGenerationOrRewriteWhenInitialRetrievalEmpty() {
        mockPlannerJson("""
                {"subQuestions":[{"question":"unknown","searchQueries":["unknown"]}],"answerStrategy":"direct"}
                """);
        RetrievalTool retrievalTool = (query, topK) -> List.of();

        AgentRuntimeService service = newService(retrievalTool, defaultLimits());
        AgentChatResponse response = service.run(
                new AgentChatRequest("s1", "unknown question", 5));

        assertFalse(response.grounded());
        assertEquals("ABSTAINED", response.status());
        assertTrue(response.evidence().isEmpty());
        assertTrue(response.answer().contains("未检索到"));
        verify(answerGenerator, never()).answer(anyString(), anyString());
        verify(rewriteService, never()).rewrite(anyString(), anyList(), anyString());
        verify(repairService, never()).repair(anyString(), anyString(), anyList(), anyString());
    }

    @Test
    void shouldFallbackToOriginalQuestionWhenPlannerReturnsInvalidJson() {
        mockPlannerJson("not a valid json at all");
        RetrievalTool retrievalTool = mock(RetrievalTool.class);
        when(retrievalTool.retrieve("我的原问题", 5))
                .thenReturn(List.of(evidence("doc-1", "相关内容")));
        when(answerGenerator.answer(anyString(), anyString()))
                .thenReturn("答案依据 [证据 doc-1]。");

        AgentRuntimeService service = newService(retrievalTool, defaultLimits());
        AgentChatResponse response = service.run(
                new AgentChatRequest("s-invalid-json", "我的原问题", 5));

        verify(retrievalTool, times(1)).retrieve("我的原问题", 5);
        assertTrue(response.grounded());
        assertEquals("COMPLETE", response.status());
    }

    @Test
    void shouldClampQueriesToMaxInitialQueriesAndMaxSubQuestions() {
        // Planner 返回 5 个子问题，Runtime 只能使用前 3 个（maxSubQuestions=3, maxInitialQueries=3）
        mockPlannerJson("""
                {
                  "subQuestions": [
                    {"question":"p1","searchQueries":["q1","q1-extra"]},
                    {"question":"p2","searchQueries":["q2"]},
                    {"question":"p3","searchQueries":["q3"]},
                    {"question":"p4","searchQueries":["q4"]},
                    {"question":"p5","searchQueries":["q5"]}
                  ],
                  "answerStrategy": "merge"
                }
                """);
        RetrievalTool retrievalTool = mock(RetrievalTool.class);
        when(retrievalTool.retrieve(anyString(), anyInt())).thenReturn(List.of());

        AgentRuntimeService service = newService(retrievalTool, defaultLimits());
        service.run(new AgentChatRequest("s2", "multi part question", 5));

        verify(retrievalTool, times(3)).retrieve(anyString(), anyInt());
    }

    @Test
    void shouldRespectTotalToolCallBudget() {
        // maxToolCalls = 2，即使 maxInitialQueries = 3 也只能调用 2 次
        mockPlannerJson("""
                {
                  "subQuestions": [
                    {"question":"p1","searchQueries":["q1"]},
                    {"question":"p2","searchQueries":["q2"]},
                    {"question":"p3","searchQueries":["q3"]}
                  ],
                  "answerStrategy": "merge"
                }
                """);
        AgentLimits tightLimits = new AgentLimits(3, 3, 1, 2, 20, 12, 12_000, Duration.ofSeconds(55));
        RetrievalTool retrievalTool = mock(RetrievalTool.class);
        when(retrievalTool.retrieve(anyString(), anyInt()))
                .thenReturn(List.of(evidence("doc-1", "内容")));
        when(answerGenerator.answer(anyString(), anyString()))
                .thenReturn("无引用的回答");

        AgentRuntimeService service = newService(retrievalTool, tightLimits);
        service.run(new AgentChatRequest("s-budget", "question", 5));

        // 2 次初次检索已达 maxToolCalls=2，不能再触发补充检索
        verify(retrievalTool, times(2)).retrieve(anyString(), anyInt());
        verify(rewriteService, never()).rewrite(anyString(), anyList(), anyString());
    }

    @Test
    void shouldRunSupplementalRetrievalAndRepairOnceWhenFirstGroundingFails() {
        mockPlannerJson("""
                {"subQuestions":[{"question":"q","searchQueries":["q"]}],"answerStrategy":"direct"}
                """);
        RetrievalTool retrievalTool = mock(RetrievalTool.class);
        when(retrievalTool.retrieve("q", 5))
                .thenReturn(List.of(evidence("doc-1", "方法 A")));
        when(retrievalTool.retrieve("q-supplement", 5))
                .thenReturn(List.of(evidence("doc-2", "实验结果提升 10%")));

        when(answerGenerator.answer(anyString(), anyString()))
                .thenReturn("论文采用了方法 A，未带引用。");
        when(rewriteService.rewrite(anyString(), anyList(), anyString()))
                .thenReturn(List.of("q-supplement", "q-ignored-second"));
        when(repairService.repair(anyString(), anyString(), anyList(), anyString()))
                .thenReturn("论文采用了方法 A [证据 doc-1]，实验结果提升 10% [证据 doc-2]。");

        AgentRuntimeService service = newService(retrievalTool, defaultLimits());
        AgentChatResponse response = service.run(
                new AgentChatRequest("s3", "采用了什么方法？", 5));

        assertTrue(response.grounded());
        assertEquals("COMPLETE_REPAIRED", response.status());
        assertTrue(response.plan().contains("rewrite_once"));
        assertTrue(response.plan().contains("repair_once"));
        assertTrue(response.plan().contains("grounding_check_passed_after_repair"));
        verify(rewriteService, times(1)).rewrite(anyString(), anyList(), anyString());
        verify(repairService, times(1)).repair(anyString(), anyString(), anyList(), anyString());
        verify(retrievalTool, times(1)).retrieve("q-supplement", 5);
    }

    @Test
    void shouldAbstainWhenRepairStillFailsGrounding() {
        mockPlannerJson("""
                {"subQuestions":[{"question":"q","searchQueries":["q"]}],"answerStrategy":"direct"}
                """);
        RetrievalTool retrievalTool = (query, topK) ->
                List.of(evidence("doc-1", "论文采用方法 A"));
        when(answerGenerator.answer(anyString(), anyString()))
                .thenReturn("引用了不存在的 [证据 doc-999]。");
        when(rewriteService.rewrite(anyString(), anyList(), anyString()))
                .thenReturn(List.of());
        when(repairService.repair(anyString(), anyString(), anyList(), anyString()))
                .thenReturn("依然引用了不存在的 [证据 doc-888]。");

        AgentRuntimeService service = newService(retrievalTool, defaultLimits());
        AgentChatResponse response = service.run(
                new AgentChatRequest("s4", "实验结果如何？", 5));

        assertFalse(response.grounded());
        assertEquals("ABSTAINED", response.status());
        assertTrue(response.plan().contains("grounding_check_failed_after_repair"));
        assertTrue(response.answer().contains("无法确认"));
        verify(repairService, times(1)).repair(anyString(), anyString(), anyList(), anyString());
    }

    @Test
    void shouldDegradeGracefullyWhenRetrievalToolThrowsException() {
        mockPlannerJson("""
                {"subQuestions":[{"question":"q","searchQueries":["q"]}],"answerStrategy":"direct"}
                """);
        RetrievalTool retrievalTool = (query, topK) -> {
            throw new RuntimeException("Milvus timeout");
        };

        AgentRuntimeService service = newService(retrievalTool, defaultLimits());
        AgentChatResponse response = service.run(
                new AgentChatRequest("s-err", "test", 5));

        assertFalse(response.grounded());
        assertEquals("ABSTAINED", response.status());
        ArgumentCaptor<AgentTaskState> captor = ArgumentCaptor.forClass(AgentTaskState.class);
        verify(taskStore, atLeastOnce()).save(captor.capture());
        AgentTaskState lastState = captor.getValue();
        assertEquals("ABSTAINED", lastState.finalStatus());
        assertTrue(lastState.events().stream()
                .anyMatch(e -> "retrieve".equals(e.step()) && !e.success()
                        && "retrieval_error".equals(e.errorType())));
    }

    @Test
    void shouldDeduplicateEvidenceAcrossQueries() {
        mockPlannerJson("""
                {
                  "subQuestions": [
                    {"question":"p1","searchQueries":["q1"]},
                    {"question":"p2","searchQueries":["q2"]}
                  ],
                  "answerStrategy": "merge"
                }
                """);
        Evidence shared = evidence("doc-1", "重复内容");
        RetrievalTool retrievalTool = (query, topK) -> {
            if ("q1".equals(query)) {
                return List.of(shared, evidence("doc-2", "内容二"));
            }
            return List.of(shared);
        };
        when(answerGenerator.answer(anyString(), anyString()))
                .thenReturn("综合 [证据 doc-1] 与 [证据 doc-2]。");

        AgentRuntimeService service = newService(retrievalTool, defaultLimits());
        AgentChatResponse response = service.run(
                new AgentChatRequest("s8", "q1 q2", 5));

        assertEquals(2, response.evidence().size());
        assertTrue(response.grounded());
        assertEquals("COMPLETE", response.status());
    }

    @Test
    void shouldSkipConversationMemoryWhenSessionIdMissing() {
        mockPlannerJson("""
                {"subQuestions":[{"question":"q","searchQueries":["q"]}],"answerStrategy":"direct"}
                """);
        RetrievalTool retrievalTool = (query, topK) ->
                List.of(evidence("doc-1", "内容"));
        when(answerGenerator.answer(anyString(), anyString()))
                .thenReturn("依据 [证据 doc-1]。");

        AgentRuntimeService service = newService(retrievalTool, defaultLimits());
        AgentChatResponse response = service.run(
                new AgentChatRequest(null, "问题", 5));

        assertTrue(response.grounded());
        assertEquals("COMPLETE", response.status());
        verify(memory, never()).append(anyString(), anyString(), anyString());
    }

    @Test
    void shouldThrowOnBlankQuestion() {
        AgentRuntimeService service = newService((query, topK) -> List.of(), defaultLimits());

        assertThrows(IllegalArgumentException.class,
                () -> service.run(new AgentChatRequest("s7", "  ", null)));
    }
}
