package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 引用/证据校验器（MVP 版）。
 * 至少校验回答中的引用 ID 是否全部来自本次实际纳入上下文的检索结果。
 * 它不能完全证明语义忠实性；后续应通过 DeepEval 或独立 LLM judge 做离线/抽样评估。
 */
@Component
public class GroundingChecker {

    private static final Pattern CITATION = Pattern.compile("\\[证据\\s+([^\\]\\s]+)");

    /**
     * 检查回答中的引用是否有效。
     *
     * @param answer   生成的回答文本
     * @param evidence 本次纳入上下文的证据列表
     * @return 至少存在一个引用、所有引用 ID 均可回查且证据非空时返回 true
     */
    public boolean hasValidCitations(String answer, List<Evidence> evidence) {
        Set<String> allowedIds = evidence.stream()
                .map(Evidence::documentId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Matcher matcher = CITATION.matcher(answer == null ? "" : answer);
        boolean foundCitation = false;

        while (matcher.find()) {
            foundCitation = true;
            if (!allowedIds.contains(matcher.group(1))) {
                return false;
            }
        }

        return foundCitation && !allowedIds.isEmpty();
    }
}
