package com.redis.demo.spring.ai.agent;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Agent 阶段耗时和事件计数。
 * 标签不带原始 query、sessionId 或 documentId，避免高基数标签和敏感信息泄漏。
 */
@Component
public class AgentTelemetry {

    private final MeterRegistry meterRegistry;

    public AgentTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 对指定阶段计时。
     *
     * @param stage     阶段名称（plan / retrieve / rewrite / generate / grounding / repair）
     * @param operation 要执行的操作
     * @return 操作结果
     */
    public <T> T time(String stage, Callable<T> operation) throws Exception {
        long start = System.nanoTime();
        boolean success = false;

        try {
            T result = operation.call();
            success = true;
            return result;
        } finally {
            long elapsed = System.nanoTime() - start;

            meterRegistry.timer(
                    "rag.agent.stage",
                    "stage", stage,
                    "success", Boolean.toString(success)
            ).record(elapsed, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 递增指定计数器。
     *
     * @param name    指标名称
     * @param outcome 结果标签
     */
    public void increment(String name, String outcome) {
        meterRegistry.counter(
                name,
                "outcome", outcome
        ).increment();
    }
}
