package com.redis.demo.spring.ai.service;

import com.redis.demo.spring.ai.memory.ConversationMemory;
import com.redis.demo.spring.ai.model.AgentChatHistoryResponse;
import com.redis.demo.spring.ai.model.AgentChatHistoryResponse.AgentHistoryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 对话历史读取服务。
 * 只服务 Agentic RAG 通道：从 ConversationMemory（Redis 短期会话记忆）
 * 读取按时间正序的 role/content 消息，组装为前端可渲染的历史响应；
 * 旧 Native RAG 的 /chat/{chatId} 链路不经过此服务。
 *
 * 兼容策略：
 * - sessionId 为空：返回空历史（Controller 层已先做参数校验，这里是兜底）；
 * - 会话不存在或历史为空：recent() 本身返回空列表，正常映射为空消息响应；
 * - Redis 读取失败：包装为 IllegalStateException，由 Controller 转成 5xx。
 */
@Service
public class AgentHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(AgentHistoryService.class);

    private final ConversationMemory memory;

    public AgentHistoryService(ConversationMemory memory) {
        this.memory = memory;
    }

    public AgentChatHistoryResponse history(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new AgentChatHistoryResponse("", List.of());
        }

        List<ConversationMemory.Turn> turns;
        try {
            turns = memory.recent(sessionId);
        } catch (RuntimeException ex) {
            logger.warn("Failed to read conversation history for session {}: {}",
                    sessionId, ex.getMessage());
            throw new IllegalStateException("读取会话历史失败，请稍后重试", ex);
        }

        List<AgentHistoryMessage> messages = turns.stream()
                .filter(turn -> turn.content() != null && !turn.content().isBlank())
                .map(turn -> new AgentHistoryMessage(normalizeRole(turn.role()), turn.content()))
                .toList();
        return new AgentChatHistoryResponse(sessionId, messages);
    }

    /**
     * 记忆层当前只写入 user/assistant；出现脏数据时统一按 assistant 渲染，
     * 避免前端拿到未知角色后无法决定气泡朝向。
     */
    private String normalizeRole(String role) {
        return "user".equals(role) ? "user" : "assistant";
    }
}
