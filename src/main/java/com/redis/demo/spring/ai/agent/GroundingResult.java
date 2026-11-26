package com.redis.demo.spring.ai.agent;

import java.util.List;

/**
 * Grounding 检查结果。
 * 不仅返回 boolean，还包含失败原因，供 Query Rewrite 和拒答决策使用。
 *
 * @param grounded          是否通过校验
 * @param unsupportedClaims 不被证据支持的声明列表
 * @param invalidCitations  无效的引用 ID 列表
 * @param feedback          反馈说明（供 rewrite / repair 使用）
 */
public record GroundingResult(
        boolean grounded,
        List<String> unsupportedClaims,
        List<String> invalidCitations,
        String feedback
) {
}
