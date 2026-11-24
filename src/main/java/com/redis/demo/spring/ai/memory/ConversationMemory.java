package com.redis.demo.spring.ai.memory;

import java.util.List;

/**
 * 短期会话记忆接口。
 * 以 sessionId 为明确会话主键，保存最近若干轮消息；
 * “用户消息/模型回答”与“检索证据/工具轨迹”分开存储，避免把工具数据当成对话记忆。
 */
public interface ConversationMemory {

    /**
     * 读取该会话最近的对话轮次（按时间正序）。
     *
     * @param sessionId 会话 ID
     * @return 最近的对话轮次；无历史时返回空列表
     */
    List<Turn> recent(String sessionId);

    /**
     * 追加一条对话消息，并执行长度裁剪与 TTL 刷新。
     *
     * @param sessionId 会话 ID
     * @param role      角色（user / assistant）
     * @param content   消息内容
     */
    void append(String sessionId, String role, String content);

    /**
     * 单轮对话消息。
     */
    record Turn(String role, String content) {
    }
}
