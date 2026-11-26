package com.redis.demo.spring.ai.memory;

import com.redis.demo.spring.ai.model.AgentTaskState;

import java.util.Optional;

/**
 * Agent 任务状态存储接口。
 * 用于持久化任务追踪状态，供诊断、评估和状态查询 API 使用。
 */
public interface AgentTaskStore {

    /**
     * 保存任务状态。
     *
     * @param state 任务状态快照
     */
    void save(AgentTaskState state);

    /**
     * 按 traceId 查找任务状态。
     *
     * @param traceId 追踪 ID
     * @return 任务状态（如果存在）
     */
    Optional<AgentTaskState> find(String traceId);
}
