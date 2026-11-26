package com.redis.demo.spring.ai.model;

import java.time.Instant;
import java.util.List;

/**
 * Agent 工具/阶段事件记录。
 * 用于任务追踪和指标统计，不保存原始 query 文本（避免隐私泄露），仅保留 hash。
 *
 * @param step           阶段名称（plan / retrieve / rewrite / generate / grounding / repair）
 * @param toolName       工具名称（如 hybrid_retrieve）
 * @param queryHash      检索 query 的 SHA-256 前缀 hash
 * @param documentIds    检索到的 document ID 列表
 * @param durationMillis 阶段耗时（毫秒）
 * @param success        是否成功
 * @param errorType      错误类型（成功时为 null）
 * @param timestamp      事件时间戳
 */
public record AgentTraceEvent(
        String step,
        String toolName,
        String queryHash,
        List<String> documentIds,
        long durationMillis,
        boolean success,
        String errorType,
        Instant timestamp
) {
}
