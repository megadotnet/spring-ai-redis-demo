package com.redis.demo.spring.ai.model;

import java.util.List;

/**
 * Agent 聊天响应。
 * Stage 2：增加 status 字段区分 COMPLETE / COMPLETE_REPAIRED / ABSTAINED 等终态。
 *
 * @param traceId  本次请求的追踪 ID
 * @param answer   最终回答（或澄清/拒答文案）
 * @param evidence 实际进入上下文的证据列表
 * @param plan     Agent 执行步骤记录
 * @param status   终态状态（COMPLETE / COMPLETE_REPAIRED / ABSTAINED / ERROR）
 * @param grounded 答案是否通过引用校验
 */
public record AgentChatResponse(
        String traceId,
        String answer,
        List<Evidence> evidence,
        List<String> plan,
        String status,
        boolean grounded
) {
    /**
     * Stage 1 兼容构造：不带 status 字段时默认推断。
     */
    public AgentChatResponse(
            String traceId,
            String answer,
            List<Evidence> evidence,
            List<String> plan,
            boolean grounded) {
        this(traceId, answer, evidence, plan,
                grounded ? "COMPLETE" : "UNKNOWN", grounded);
    }
}
