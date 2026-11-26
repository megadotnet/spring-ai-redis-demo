package com.redis.demo.spring.ai.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Agent 统一限制配置。
 * 所有阶段的预算上限集中管理，确保整体预算小于 Controller 超时。
 */
@ConfigurationProperties(prefix = "agent.limits")
public record AgentLimits(
        int maxSubQuestions,
        int maxInitialQueries,
        int maxSupplementalQueries,
        int maxToolCalls,
        int maxTopK,
        int maxEvidence,
        int maxContextCharacters,
        Duration totalTimeout
) {
    public AgentLimits {
        maxSubQuestions = positiveOrDefault(maxSubQuestions, 3);
        maxInitialQueries = positiveOrDefault(maxInitialQueries, 3);
        maxSupplementalQueries = positiveOrDefault(maxSupplementalQueries, 1);
        maxToolCalls = positiveOrDefault(maxToolCalls, 4);
        maxTopK = positiveOrDefault(maxTopK, 20);
        maxEvidence = positiveOrDefault(maxEvidence, 12);
        maxContextCharacters = positiveOrDefault(maxContextCharacters, 12_000);
        totalTimeout = totalTimeout == null
                ? Duration.ofSeconds(55)
                : totalTimeout;
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
