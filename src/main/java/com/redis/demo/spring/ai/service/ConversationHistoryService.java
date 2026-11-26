package com.redis.demo.spring.ai.service;

import com.redis.demo.spring.ai.memory.ConversationMemory;
import com.redis.demo.spring.ai.model.ConversationHistoryResponse;
import com.redis.demo.spring.ai.model.ConversationHistoryResponse.HistoryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对话历史读取服务，服务两条通道：
 * - Agentic RAG：AgentChatController 的 GET /api/agent/history/{sessionId}；
 * - Native RAG：RagController 的 GET /chat/history/{chatId}。
 * 两条通道共用 ConversationMemory（Redis，key 前缀 rag:session:）的 role/content 持久化，
 * 因此同一会话 ID 在两条通道间回切时历史不丢。
 *
 * 兼容策略：
 * - sessionId 为空：返回空历史（Controller 层已先做参数校验，这里是兜底）；
 * - 会话不存在或历史为空：recent() 本身返回空列表，正常映射为空消息响应；
 * - Redis 读取失败：包装为 IllegalStateException，由 Controller 转成 5xx。
 */
@Service
public class ConversationHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationHistoryService.class);

    private final ConversationMemory memory;

    public ConversationHistoryService(ConversationMemory memory) {
        this.memory = memory;
    }

    public ConversationHistoryResponse history(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new ConversationHistoryResponse("", List.of());
        }

        List<ConversationMemory.Turn> turns;
        try {
            turns = memory.recent(sessionId);
        } catch (RuntimeException ex) {
            logger.warn("Failed to read conversation history for session {}: {}",
                    sessionId, ex.getMessage());
            throw new IllegalStateException("读取会话历史失败，请稍后重试", ex);
        }

        List<HistoryMessage> messages = turns.stream()
                .filter(turn -> turn.content() != null && !turn.content().isBlank())
                .map(turn -> new HistoryMessage(normalizeRole(turn.role()), turn.content()))
                .toList();
        return new ConversationHistoryResponse(sessionId, messages);
    }

    /**
     * 记忆层当前只写入 user/assistant；出现脏数据时统一按 assistant 渲染，
     * 避免前端拿到未知角色后无法决定气泡朝向。
     */
    private String normalizeRole(String role) {
        return "user".equals(role) ? "user" : "assistant";
    }
}
