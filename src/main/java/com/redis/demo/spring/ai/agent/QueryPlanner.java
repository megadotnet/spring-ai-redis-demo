package com.redis.demo.spring.ai.agent;

import java.util.List;

/**
 * 查询规划器接口。
 * 规划器只输出检索任务（query 列表 / 是否需要澄清），不直接执行检索或生成最终答案。
 * MVP 使用规则实现保证行为可复现；后续可替换为受 schema 约束的 LLM 规划器，
 * 但无论由谁规划，检索调用都必须由 Java Runtime 控制。
 */
public interface QueryPlanner {

    QueryPlan plan(String question);

    /**
     * 结构化查询计划。
     *
     * @param retrievalQueries      需要执行的检索 query 列表（数量受限）
     * @param needsClarification    是否需要向用户澄清
     * @param clarificationQuestion 澄清问题文案
     */
    record QueryPlan(
            List<String> retrievalQueries,
            boolean needsClarification,
            String clarificationQuestion
    ) {
    }
}
