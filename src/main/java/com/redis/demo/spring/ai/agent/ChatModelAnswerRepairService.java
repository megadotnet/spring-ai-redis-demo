package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 ChatModel 的答案修正实现。
 * 要求修正只使用已有证据，不编造、修改或引用证据中没有的 documentId。
 */
@Component
public class ChatModelAnswerRepairService implements AnswerRepairService {

    private final ChatModel chatModel;

    public ChatModelAnswerRepairService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String repair(
            String question,
            String previousAnswer,
            List<Evidence> evidence,
            String checkerFeedback) {

        String context = evidence.stream()
                .map(item -> "[证据 " + item.documentId() + "] "
                        + item.content())
                .reduce("", (left, right) -> left + "\n\n" + right);

        String system = """
                你需要修正一段知识库问答。
                仅使用给出的证据；不确定的内容应明确说资料不足。
                所有事实性结论必须引用已有的 [证据 documentId]。
                不得编造、修改或引用证据中没有的 documentId。
                """;

        String user = """
                用户问题：
                %s

                原答案：
                %s

                校验反馈：
                %s

                可用证据：
                %s

                请输出修正后的最终答案，不要解释修正过程。
                """.formatted(
                question,
                previousAnswer,
                checkerFeedback,
                context
        );

        return chatModel.call(new Prompt(List.of(
                new SystemMessage(system),
                new UserMessage(user)
        ))).getResult().getOutput().getText();
    }
}
