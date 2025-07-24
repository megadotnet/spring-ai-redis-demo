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

public class RagService {

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
		UserMessage userMessage = new UserMessage(message);
		// Assemble the complete prompt using a template
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		// Call the autowired chat model with the prompt
		ChatResponse response = chatModel.call(prompt);
		return response.getResult();
	}
	// end::retrieve[]

	private Message getSystemMessage(List<Document> similarDocuments) {
		String documents = similarDocuments.stream().map(doc -> doc.getText()).collect(Collectors.joining("\n"));
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemBeerPrompt);
		return systemPromptTemplate.createMessage(Map.of("documents", documents));
	}

}
