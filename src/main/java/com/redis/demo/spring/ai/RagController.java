package com.redis.demo.spring.ai;

import java.util.UUID;

import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 提供与聊天和文档上传相关的 HTTP 接口的控制器。
 *
 * 负责：
 * - 创建新的聊天会话标识
 * - 接收用户问题并通过 RAG 服务生成回答
 * - 文档上传占位接口
 */
@Controller
public class RagController {

    private final RagService ragService;

    /**
     * 构造函数，注入 {@link RagService}。
     *
     * @param ragService RAG 业务服务
     */
    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 开启聊天，返回一个新的会话 ID。
     *
     * @return 包含会话 ID 的消息对象
     */
    @PostMapping("/chat/startChat")
    @ResponseBody
    public Message startChat() {
        return Message.of(UUID.randomUUID().toString());
    }

    //tag::chatMessage[]
    /**
     * 接收用户输入并返回生成的回答。
     *
     * @param chatId 会话 ID
     * @param prompt 包含用户输入的请求体
     * @return 模型生成的文本消息
     */
    @PostMapping("/chat/{chatId}")
    @ResponseBody
    public Message chatMessage(@PathVariable("chatId") String chatId, @RequestBody Prompt prompt) {
        // Extract user prompt from the body and pass it to the RagService
        Generation generation = ragService.retrieve(prompt.getPrompt());
        // Reply with the generated message
        return Message.of(generation.getOutput().getText());
    }
    //end::chatMessage[]

    /**
     * 文档上传占位接口。
     *
     * @param doc 文档内容（当前不支持）
     * @return 提示当前不支持上传
     */
    @PostMapping("/documents/upload")
    @ResponseBody
    public String uploadDocument(String doc) {
        return "Document upload not supported";
    }

    public static class Message {

        private String message;

        /**
         * 获取消息内容。
         *
         * @return 消息文本
         */
        public String getMessage() {
            return message;
        }

        /**
         * 设置消息内容。
         *
         * @param message 消息文本
         */
        public void setMessage(String message) {
            this.message = message;
        }

        /**
         * 构建一个包含指定文本的消息对象。
         *
         * @param message 文本内容
         * @return 消息对象
         */
        public static Message of(String message) {
            Message response = new Message();
            response.setMessage(message);
            return response;
        }

    }

    public static class Prompt {

        private String prompt;

        /**
         * 获取用户输入内容。
         *
         * @return 输入文本
         */
        public String getPrompt() {
            return prompt;
        }

        /**
         * 设置用户输入内容。
         *
         * @param prompt 输入文本
         */
        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

    }

}
