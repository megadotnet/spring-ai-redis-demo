package com.redis.demo.spring.ai;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * RAG 业务服务，负责：
 * - 基于用户问题从向量库检索相关文档
 * - 组装系统与用户消息，调用聊天模型生成答案
 */
public class RagService {

	@Value("classpath:/prompts/system-qa.st")
	private Resource systemBeerPrompt;

	@Value("${topk:10}")
	private int topK;

	private final ChatModel chatModel;

	private final VectorStore store;

	/**
	 * 构造函数。
	 *
	 * @param chatModel 聊天模型
	 * @param store 向量存储（检索相关文档）
	 */
	public RagService(ChatModel chatModel, VectorStore store) {
		this.chatModel = chatModel;
		this.store = store;
	}

	// tag::retrieve[]
	/**
	 * 根据用户输入进行检索并生成答案。
	 *
	 * 处理流程：
	 * 1. 构造检索请求，使用 Top-K 相似度搜索
	 * 2. 从向量库检索相关文档并拼接为系统提示
	 * 3. 调用聊天模型生成回答
	 *
	 * @param message 用户输入
	 * @return 模型生成结果
	 */
	public Generation retrieve(String message) {
		// Create a search request to find relevant documents
		SearchRequest request = SearchRequest.builder().query(message).topK(topK).build();
		// Query Redis for the top K documents most relevant to the input message
		List<Document> docs = store.similaritySearch(request);
		Message systemMessage = getSystemMessage(docs);
		UserMessage userMessage = new UserMessage(message);
		// Assemble the complete prompt using a template
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		// Call the autowired chat model with the prompt
		ChatResponse response = chatModel.call(prompt);
		return response.getResult();
	}
	// end::retrieve[]

	/**
	 * 根据检索到的相似文档构建系统消息。
	 *
	 * @param similarDocuments 相似文档列表
	 * @return 系统消息（包含上下文文档内容）
	 */
	private Message getSystemMessage(List<Document> similarDocuments) {
		// 将相似的文档列表中的文本拼接成一个字符串
		String documents = similarDocuments.stream().map(doc -> doc.getText()).collect(Collectors.joining("\n"));
		// 创建一个系统提示模板，使用系统啤酒提示
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemBeerPrompt);
		// 使用模板创建消息，将拼接的文档字符串作为参数
		return systemPromptTemplate.createMessage(Map.of("documents", documents));
	}

}
