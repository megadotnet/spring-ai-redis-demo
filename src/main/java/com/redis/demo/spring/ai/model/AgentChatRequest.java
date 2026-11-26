package com.redis.demo.spring.ai.model;

/**
 * Agent 聊天请求。
 *
 * @param sessionId 会话 ID，用于短期记忆与任务状态关联
 * @param question  用户问题
 * @param topK      每次检索的候选数量（可选，Runtime 会做上下界校验）
 */
public record AgentChatRequest(
        String sessionId,
        String question,
        Integer topK
) {
}
