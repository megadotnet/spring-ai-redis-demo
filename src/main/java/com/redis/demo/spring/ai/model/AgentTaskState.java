package com.redis.demo.spring.ai.model;

import java.time.Instant;
import java.util.List;

/**
 * Agent 任务状态快照。
 * 用于诊断、评估和任务状态查询 API，不保存完整文档正文。
 *
 * @param traceId     本次请求的追踪 ID
 * @param sessionId   会话 ID
 * @param status      当前状态（PLANNING / RETRIEVING / GENERATING 等）
 * @param startedAt   任务开始时间
 * @param updatedAt   最后更新时间
 * @param events      工具/阶段事件列表
 * @param finalStatus 最终状态（COMPLETE / COMPLETE_REPAIRED / ABSTAINED / ERROR）
 */
public record AgentTaskState(
        String traceId,
        String sessionId,
        String status,
        Instant startedAt,
        Instant updatedAt,
        List<AgentTraceEvent> events,
        String finalStatus
) {
}
