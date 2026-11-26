package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性引用校验器（Stage 2 增强版）。
 * 检查回答中引用的 documentId 是否全部属于本次检索证据。
 * 返回结构化 GroundingResult，包含失败原因和反馈。
 *
 * 重要边界：引用 ID 合法不等于语义正确。此校验只判断引用格式和 ID 归属，
 * faithfulness/correctness 仍要通过 DeepEval 回归及人工抽检评估。
 */
@Component
public class CitationValidator {

    private static final Pattern CITATION =
            Pattern.compile("\\[证据\\s+([^\\]\\s]+)\\]");

    /**
     * 校验回答中的引用 ID 是否有效。
     *
     * @param answer   生成的回答文本
     * @param evidence 本次纳入上下文的证据列表
     * @return 结构化校验结果
     */
    public GroundingResult validate(String answer, List<Evidence> evidence) {
        Set<String> allowedIds = new HashSet<>();
        for (Evidence item : evidence) {
            if (item.documentId() != null && !item.documentId().isBlank()) {
                allowedIds.add(item.documentId());
            }
        }

        Set<String> foundIds = new HashSet<>();
        Set<String> invalidIds = new HashSet<>();

        Matcher matcher = CITATION.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            String id = matcher.group(1);
            foundIds.add(id);
            if (!allowedIds.contains(id)) {
                invalidIds.add(id);
            }
        }

        boolean grounded = !allowedIds.isEmpty()
                && !foundIds.isEmpty()
                && invalidIds.isEmpty();

        String feedback = grounded
                ? "引用 ID 均属于当前检索证据。"
                : buildFeedback(allowedIds, foundIds, invalidIds);

        return new GroundingResult(
                grounded,
                grounded ? List.of() : List.of("引用或证据覆盖不足"),
                List.copyOf(invalidIds),
                feedback
        );
    }

    private String buildFeedback(
            Set<String> allowedIds,
            Set<String> foundIds,
            Set<String> invalidIds) {
        if (allowedIds.isEmpty()) {
            return "证据列表为空，无法进行引用校验。";
        }
        if (foundIds.isEmpty()) {
            return "答案中未包含任何引用标注 [证据 documentId]。";
        }
        return "答案引用了本次检索未返回的 documentId：" + invalidIds + "。";
    }
}
