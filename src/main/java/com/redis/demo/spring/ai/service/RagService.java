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

/**
 * RAG 检索增强生成服务
 * 支持三种检索模式：纯向量检索、向量检索+Rerank、混合检索（BM25+向量）
 */
public class RagService {

	private static final Logger logger = LoggerFactory.getLogger(RagService.class);

	@Value("classpath:/prompts/system-qa.st")
	private Resource systemBeerPrompt;

	@Value("${topk:10}")
	private int topK;

	@Value("${rerank.enabled:false}")
	private boolean rerankEnabled;

	@Value("${rerank.topN:5}")
	private int rerankTopN;

	@Value("${hybrid.search.enabled:false}")
	private boolean hybridSearchEnabled;

	private final ChatModel chatModel;

	private final VectorStore store;

	private final RerankService rerankService;

	private final HybridSearchService hybridSearchService;

	/**
	 * 构造函数（不启用 rerank 和混合检索）
	 */
	public RagService(ChatModel chatModel, VectorStore store) {
		this(chatModel, store, null, null);
	}

	/**
	 * 构造函数（支持可选的 rerank 服务，不启用混合检索）
	 */
	public RagService(ChatModel chatModel, VectorStore store, RerankService rerankService) {
		this(chatModel, store, rerankService, null);
	}

	/**
	 * 构造函数（完整版：支持可选的 rerank 服务和混合检索服务）
	 */
	public RagService(ChatModel chatModel, VectorStore store, RerankService rerankService,
			HybridSearchService hybridSearchService) {
		this.chatModel = chatModel;
		this.store = store;
		this.rerankService = rerankService;
		this.hybridSearchService = hybridSearchService;
	}

	// tag::retrieve[]
	public Generation retrieve(String message) {
		List<Document> docs;

		// 根据配置选择检索策略
		if (hybridSearchEnabled && hybridSearchService != null) {
			// 混合检索模式：BM25 + 向量检索
			logger.info("Using hybrid search (BM25 + vector) mode");
			docs = hybridSearchService.hybridSearch(message, topK);
			logger.info("Retrieved {} documents from hybrid search", docs.size());
		} else {
			// 纯向量检索模式
			SearchRequest request = SearchRequest.builder().query(message).topK(topK).build();
			docs = store.similaritySearch(request);
			logger.info("Retrieved {} documents from vector store", docs.size());
		}

		// 可选的 rerank 精排流程（对两种检索模式都生效）
		if (rerankEnabled && rerankService != null) {
			logger.info("Rerank is enabled, performing rerank with topN={}", rerankTopN);
			docs = rerankService.rerank(message, docs, rerankTopN);
			logger.info("After rerank: {} documents selected", docs.size());
		}

		Message systemMessage = getSystemMessage(docs);
		UserMessage userMessage = new UserMessage(message);
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		ChatResponse response = chatModel.call(prompt);
		return response.getResult();
	}
	// end::retrieve[]

	// 根据相似的文档列表获取系统消息
	private Message getSystemMessage(List<Document> similarDocuments) {
		String documents = similarDocuments.stream().map(doc -> doc.getText()).collect(Collectors.joining("\n"));
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemBeerPrompt);
		return systemPromptTemplate.createMessage(Map.of("documents", documents));
	}

}
