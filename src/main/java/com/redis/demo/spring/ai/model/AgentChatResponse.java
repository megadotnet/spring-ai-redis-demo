package com.redis.demo.spring.ai.model;

import java.util.List;

/**
 * Agent 聊天响应。
 * 包含回答、证据引用、计划步骤和 traceId，方便前端展示、排查和评估。
 *
 * @param traceId  本次请求的追踪 ID
 * @param answer   最终回答（或澄清/拒答文案）
 * @param evidence 实际进入上下文的证据列表
 * @param plan     Agent 执行步骤记录
 * @param grounded 答案是否通过引用校验
 */
public record AgentChatResponse(
        String traceId,
        String answer,
        List<Evidence> evidence,
        List<String> plan,
        boolean grounded
) {
}
