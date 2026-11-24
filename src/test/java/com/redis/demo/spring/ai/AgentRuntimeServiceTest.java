package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.agent.AgentRuntimeService;
import com.redis.demo.spring.ai.agent.AnswerGenerator;
import com.redis.demo.spring.ai.agent.ContextBuilder;
import com.redis.demo.spring.ai.agent.GroundingChecker;
import com.redis.demo.spring.ai.agent.QueryPlanner;
import com.redis.demo.spring.ai.agent.RetrievalTool;
import com.redis.demo.spring.ai.memory.ConversationMemory;
import com.redis.demo.spring.ai.model.AgentChatRequest;
import com.redis.demo.spring.ai.model.AgentChatResponse;
import com.redis.demo.spring.ai.model.Evidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentRuntimeService 关键路径单元测试（MVP 验收项）：
 * - 无证据时稳定拒答，且不触发生成；
 * - 超过最大检索次数时停止（有界规划循环）；
 * - 无引用/伪造引用的答案被 GroundingChecker 拦截；
 * - 引用有效的答案通过校验并写入会话记忆；
 * - 需要澄清时不执行检索；
 * - 空问题被拒绝。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentRuntimeServiceTest {

    @Mock
    private AnswerGenerator answerGenerator;

    @Mock
    private ConversationMemory memory;

    private AgentRuntimeService newService(QueryPlanner planner, RetrievalTool retrievalTool) {
        return new AgentRuntimeService(
                planner,
                retrievalTool,
                new ContextBuilder(),
                answerGenerator,
                new GroundingChecker(),
                memory
        );
    }

    private static QueryPlanner passthroughPlanner() {
        return question -> new QueryPlanner.QueryPlan(List.of(question), false, null);
    }

    private static Evidence evidence(String id, String content) {
        return new Evidence(id, content, "doc.pdf", 1, "hybrid", 0.9);
    }

    @Test
    void shouldReturnNoEvidenceMessageWhenRetrievalIsEmpty() {
        RetrievalTool retrievalTool = (query, topK) -> List.of();

        AgentRuntimeService service = newService(passthroughPlanner(), retrievalTool);

        AgentChatResponse response = service.run(
                new AgentChatRequest("s1", "unknown question", 5));

        assertFalse(response.grounded());
        assertTrue(response.evidence().isEmpty());
        assertTrue(response.answer().contains("未检索到"));
        // 无证据路径不应调用生成模型
        verify(answerGenerator, never()).answer(anyString(), anyString());
    }

    @Test
    void shouldLimitRetrievalCallsToMaxToolCalls() {
        // 规划器给出 5 个 query，Runtime 必须截断为最多 3 次检索调用
        QueryPlanner planner = question -> new QueryPlanner.QueryPlan(
                List.of("q1", "q2", "q3", "q4", "q5"), false, null);

        RetrievalTool retrievalTool = org.mockito.Mockito.mock(RetrievalTool.class);
        when(retrievalTool.retrieve(anyString(), anyInt())).thenReturn(List.of());

        AgentRuntimeService service = newService(planner, retrievalTool);
        service.run(new AgentChatRequest("s2", "multi part question", 5));

        verify(retrievalTool, times(3)).retrieve(anyString(), anyInt());
        verify(answerGenerator, never()).answer(anyString(), anyString());
    }

    @Test
    void shouldRejectAnswerWithoutValidCitations() {
        RetrievalTool retrievalTool = (query, topK) ->
                List.of(evidence("doc-1", "论文采用方法 A"));
        when(answerGenerator.answer(anyString(), anyString()))
                .thenReturn("论文采用了方法 A，但没有任何引用标注。");

        AgentRuntimeService service = newService(passthroughPlanner(), retrievalTool);
        AgentChatResponse response = service.run(
                new AgentChatRequest("s3", "采用了什么方法？", 5));

        assertFalse(response.grounded());
        assertTrue(response.plan().contains("grounding_check_failed"));
    }

    @Test
    void shouldRejectAnswerWithFabricatedCitation() {
        RetrievalTool retrievalTool = (query, topK) ->
                List.of(evidence("doc-1", "论文采用方法 A"));
        when(answerGenerator.answer(anyString(), anyString()))
                .thenReturn("实验结果如 [证据 doc-999] 所示。");

        AgentRuntimeService service = newService(passthroughPlanner(), retrievalTool);
        AgentChatResponse response = service.run(
                new AgentChatRequest("s4", "实验结果如何？", 5));

        assertFalse(response.grounded());
        assertTrue(response.plan().contains("grounding_check_failed"));
    }

    @Test
    void shouldPassGroundedAnswerAndPersistTurns() {
        RetrievalTool retrievalTool = (query, topK) ->
                List.of(evidence("doc-1", "论文采用方法 A"));
        when(answerGenerator.answer(anyString(), anyString()))
                .thenReturn("论文采用了方法 A [证据 doc-1]。");

        AgentRuntimeService service = newService(passthroughPlanner(), retrievalTool);
        AgentChatResponse response = service.run(
                new AgentChatRequest("s5", "采用了什么方法？", 5));

        assertTrue(response.grounded());
        assertTrue(response.plan().contains("grounding_check_passed"));
        assertFalse(response.traceId().isBlank());
        assertEquals(1, response.evidence().size());
        verify(memory).append("s5", "user", "采用了什么方法？");
        verify(memory).append("s5", "assistant", "论文采用了方法 A [证据 doc-1]。");
    }

    @Test
    void shouldReturnClarificationWithoutRetrieval() {
        QueryPlanner planner = question ->
                new QueryPlanner.QueryPlan(List.of(), true, "请提供需要查询的问题。");
        RetrievalTool retrievalTool = org.mockito.Mockito.mock(RetrievalTool.class);

        AgentRuntimeService service = newService(planner, retrievalTool);
        AgentChatResponse response = service.run(
                new AgentChatRequest("s6", "?", 5));

        assertEquals(List.of("clarify"), response.plan());
        assertFalse(response.grounded());
        verify(retrievalTool, never()).retrieve(anyString(), anyInt());
    }

    @Test
    void shouldThrowOnBlankQuestion() {
        AgentRuntimeService service = newService(passthroughPlanner(),
                (query, topK) -> List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.run(new AgentChatRequest("s7", "  ", null)));
    }

    @Test
    void shouldDeduplicateEvidenceAcrossQueries() {
        // 两个 query 返回相同 documentId，上下文只应保留一份证据
        Evidence shared = evidence("doc-1", "重复内容");
        RetrievalTool retrievalTool = new RetrievalTool() {
            @Override
            public List<Evidence> retrieve(String query, int topK) {
                if ("q1".equals(query)) {
                    return List.of(shared, evidence("doc-2", "内容二"));
                }
                return List.of(shared);
            }
        };
        QueryPlanner planner = question ->
                new QueryPlanner.QueryPlan(List.of("q1", "q2"), false, null);
        when(answerGenerator.answer(anyString(), anyString()))
                .thenReturn("综合 [证据 doc-1] 与 [证据 doc-2]。");

        AgentRuntimeService service = newService(planner, retrievalTool);
        AgentChatResponse response = service.run(
                new AgentChatRequest("s8", "q1 q2", 5));

        assertEquals(2, response.evidence().size());
        assertTrue(response.grounded());
    }
}
