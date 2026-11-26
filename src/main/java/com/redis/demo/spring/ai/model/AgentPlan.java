package com.redis.demo.spring.ai.model;

import java.util.List;

/**
 * Agent 规划 DTO：结构化的检索任务规划。
 *
 * @param subQuestions    拆分后的子问题列表，每个子问题包含独立的检索 query
 * @param answerStrategy  综合各子问题证据的答案策略描述
 */
public record AgentPlan(
        List<SubQuestion> subQuestions,
        String answerStrategy
) {
    /**
     * 子问题及其对应的检索查询。
     *
     * @param question       子问题描述
     * @param searchQueries  该子问题的检索 query 列表
     */
    public record SubQuestion(
            String question,
            List<String> searchQueries
    ) {
    }
}
