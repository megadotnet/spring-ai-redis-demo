package com.redis.demo.spring.ai.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class RagService {

	private static final Logger logger = LoggerFactory.getLogger(RagService.class);

	@Value("classpath:/prompts/system-qa.st")
	private Resource systemBeerPrompt;

	@Value("${topk:10}")
	private int topK;

	private final ChatModel chatModel;

	private final VectorStore store;

	public RagService(ChatModel chatModel, VectorStore store) {
		this.chatModel = chatModel;
		this.store = store;
	}

	// tag::retrieve[]
	public Generation retrieve(String message) {
		// Create a search request to find relevant documents
		SearchRequest request = SearchRequest.builder().query(message).topK(topK).build();
		// Query Redis for the top K documents most relevant to the input message
		List<Document> docs = store.similaritySearch(request);
		Message systemMessage = getSystemMessage(docs);
		logger.info("RAG return : {}", systemMessage.getText());
		UserMessage userMessage = new UserMessage(message);
		// Assemble the complete prompt using a template
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		// Call the autowired chat model with the prompt
		ChatResponse response = chatModel.call(prompt);
		return response.getResult();
	}
	// end::retrieve[]

// 根据相似的文档列表获取系统消息
	private Message getSystemMessage(List<Document> similarDocuments) {
		// 将相似的文档列表中的文本拼接成一个字符串
		String documents = similarDocuments.stream().map(doc -> doc.getText()).collect(Collectors.joining("\n"));
		// 创建一个系统提示模板，使用系统啤酒提示
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemBeerPrompt);
		// 使用模板创建消息，将拼接的文档字符串作为参数
		return systemPromptTemplate.createMessage(Map.of("documents", documents));
	}

}
