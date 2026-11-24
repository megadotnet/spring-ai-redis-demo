package com.redis.demo.spring.ai.agent;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 答案生成器：从 RagService 中拆分出的确定性生成能力。
 * 沿用项目已有的 ChatModel.call(Prompt) 风格，使用结构化系统提示要求引用证据。
 */
@Component
public class AnswerGenerator {

    private final ChatModel chatModel;

    public AnswerGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 基于检索上下文生成回答。
     *
     * @param question 用户问题
     * @param context  ContextBuilder 组装的、带引用标记的证据文本
     * @return 模型生成的回答文本
     */
    public String answer(String question, String context) {
        String systemText = """
                你是一个基于知识库证据回答问题的助手。
                只使用提供的证据作答；证据不足时明确说明。
                对事实性结论必须使用证据标注，格式为 [证据 documentId]。
                不要编造证据编号。

                可用证据：
                %s
                """.formatted(context);

        List<Message> messages = List.of(
                new SystemMessage(systemText),
                new UserMessage(question)
        );
        Prompt prompt = new Prompt(messages);

        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }
}
