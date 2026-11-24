package com.redis.demo.spring.ai.agent;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * MVP 规则规划器：将明显的多问题按常见连接词拆分为多个检索 query。
 * 这只是可运行的起点，上线前应基于实际问题类型完善规则，
 * 或在服务端校验下替换为 LLM 规划器。
 */
@Component
public class RuleBasedQueryPlanner implements QueryPlanner {

    private static final int MAX_QUERIES = 3;

    @Override
    public QueryPlan plan(String question) {
        if (question == null || question.isBlank()) {
            return new QueryPlan(
                    List.of(),
                    true,
                    "请提供需要查询的问题。"
            );
        }

        // MVP：仅按常见连接词拆分；不要把不可靠的拆分规则当作最终方案。
        String[] parts = question.split("并且|同时|另外|以及|；|\\?");
        List<String> queries = Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(MAX_QUERIES)
                .toList();

        if (queries.isEmpty()) {
            queries = List.of(question.trim());
        }

        return new QueryPlan(queries, false, null);
    }
}
