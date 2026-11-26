package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.AgentPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * LLM 规划器：将复杂问题拆分成有限数量的子问题，各子问题独立检索。
 * 输出格式限定为 JSON，解析失败时回退为"原问题作为唯一子问题"。
 *
 * 注意：规划器只产生有限的检索计划，不执行工具。
 */
@Component
public class AgentPlanner {

    private static final Logger logger = LoggerFactory.getLogger(AgentPlanner.class);

    private static final String SYSTEM_PROMPT = """
            你是检索任务规划器，不负责回答用户问题。
            将复杂问题拆成最多 3 个互不重复、可独立检索的子问题。
            每个子问题最多给出 1 个搜索 query。
            不需要拆分的问题只生成一个子问题，并将原问题作为 query。
            只返回 JSON，不要返回 Markdown 代码块。
            JSON 格式：
            {
              "subQuestions": [
                {
                  "question": "子问题",
                  "searchQueries": ["用于知识库检索的查询"]
                }
              ],
              "answerStrategy": "如何综合各子问题证据"
            }
            """;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final int maxSubQuestions;
    private final int maxQueryLength;

    @Autowired
    public AgentPlanner(ChatModel chatModel) {
        this(chatModel, 3, 500);
    }

    AgentPlanner(ChatModel chatModel, int maxSubQuestions, int maxQueryLength) {
        this.chatModel = chatModel;
        this.maxSubQuestions = maxSubQuestions;
        this.maxQueryLength = maxQueryLength;
    }

    /**
     * 规划检索任务。
     *
     * @param question 用户原始问题
     * @return 结构化规划结果
     */
    public AgentPlan plan(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question is required");
        }

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(question)
        ));

        try {
            String content = chatModel.call(prompt)
                    .getResult()
                    .getOutput()
                    .getText();

            // 尝试去除可能的 markdown 代码块包裹
            content = stripMarkdownCodeBlock(content);

            AgentPlan plan = objectMapper.readValue(content, AgentPlan.class);
            return sanitize(plan, question);
        } catch (Exception ex) {
            // 规划模型或 JSON 解析异常时退回单 query，不阻塞知识问答主路径。
            logger.warn("AgentPlanner failed, falling back to single query: {}", ex.getMessage());
            return fallback(question);
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

    private AgentPlan sanitize(AgentPlan plan, String originalQuestion) {
        if (plan == null || plan.subQuestions() == null
                || plan.subQuestions().isEmpty()) {
            return fallback(originalQuestion);
        }

        List<AgentPlan.SubQuestion> sanitized = plan.subQuestions().stream()
                .filter(sq -> sq != null
                        && sq.question() != null
                        && !sq.question().isBlank())
                .limit(maxSubQuestions)
                .map(sq -> {
                    List<String> queries = sq.searchQueries() == null
                            ? List.of()
                            : sq.searchQueries().stream()
                                    .filter(q -> q != null && !q.isBlank())
                                    .map(q -> q.substring(
                                            0, Math.min(q.length(), maxQueryLength)))
                                    .limit(1)
                                    .toList();

                    if (queries.isEmpty()) {
                        queries = List.of(sq.question().substring(
                                0, Math.min(sq.question().length(), maxQueryLength)));
                    }

                    return new AgentPlan.SubQuestion(
                            sq.question(),
                            queries
                    );
                })
                .toList();

        return sanitized.isEmpty()
                ? fallback(originalQuestion)
                : new AgentPlan(sanitized, plan.answerStrategy());
    }

    private AgentPlan fallback(String question) {
        return new AgentPlan(
                List.of(new AgentPlan.SubQuestion(question, List.of(question))),
                "基于检索证据直接回答"
        );
    }
}
