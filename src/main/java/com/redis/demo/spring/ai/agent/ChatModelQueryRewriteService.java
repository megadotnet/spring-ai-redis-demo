package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * 基于 ChatModel 的 Query Rewrite 实现。
 * 生成失败或 JSON 异常时返回空列表，让流程进入修正/拒答判断。
 *
 * 注意：只传少量证据摘要给模型，不发送完整检索文档。
 */
@Component
public class ChatModelQueryRewriteService implements QueryRewriteService {

    private static final Logger logger =
            LoggerFactory.getLogger(ChatModelQueryRewriteService.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public ChatModelQueryRewriteService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public List<String> rewrite(
            String originalQuestion,
            List<Evidence> currentEvidence,
            String checkerFeedback) {

        String system = """
                你是知识库检索 query 改写器。
                根据用户问题、已有证据摘要和证据缺口，生成最多 2 个补充检索 query。
                query 应直接用于文档检索，不要回答用户问题。
                只返回 JSON：{"queries":["query1","query2"]}
                不要返回 Markdown 代码块。
                """;

        List<String> evidencePreview = currentEvidence.stream()
                .limit(5)
                .map(e -> e.content() == null
                        ? ""
                        : e.content().substring(0,
                                Math.min(e.content().length(), 300)))
                .toList();

        String input;
        try {
            input = objectMapper.writeValueAsString(Map.of(
                    "question", originalQuestion,
                    "evidencePreview", evidencePreview,
                    "missingInformation", checkerFeedback == null
                            ? ""
                            : checkerFeedback
            ));
        } catch (Exception ex) {
            logger.warn("Failed to build rewrite input: {}", ex.getMessage());
            return List.of();
        }

        try {
            String response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(system),
                    new UserMessage(input)
            ))).getResult().getOutput().getText();

            // 尝试去除 markdown 代码块
            response = stripMarkdownCodeBlock(response);

            @SuppressWarnings("unchecked")
            Map<String, Object> result =
                    objectMapper.readValue(response, Map.class);

            Object rawQueries = result.get("queries");
            if (!(rawQueries instanceof List<?> values)) {
                return List.of();
            }

            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::trim)
                    .filter(q -> !q.isBlank())
                    .map(q -> q.substring(0, Math.min(500, q.length())))
                    .distinct()
                    .limit(2)
                    .toList();
        } catch (Exception ex) {
            logger.warn("QueryRewrite failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private String stripMarkdownCodeBlock(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
