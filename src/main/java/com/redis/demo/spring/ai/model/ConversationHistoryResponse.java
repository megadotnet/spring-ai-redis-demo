package com.redis.demo.spring.ai.model;

import java.util.List;

/**
 * 会话对话历史响应（Native RAG 与 Agentic RAG 两条通道共用）。
 * 会话记忆（ConversationMemory）当前只持久化 role/content，
 * traceId、evidence、plan、grounded 等 Agent 元信息未落库，
 * 因此历史接口只返回可恢复的文本消息，不伪造元信息字段；
 * 后续若记忆层扩展了元信息持久化，再在此 DTO 上补充对应字段。
 *
 * @param sessionId 会话 ID（native 通道即 chatId，两者同为 startChat 分配的 UUID）
 * @param messages  按时间正序的历史消息列表；会话不存在或无历史时为空列表
 */
public record ConversationHistoryResponse(
        String sessionId,
        List<HistoryMessage> messages
) {

    /**
     * 单条可恢复的历史消息。
     *
     * @param role    角色（user / assistant）
     * @param content 消息文本
     */
    public record HistoryMessage(String role, String content) {
    }
}
