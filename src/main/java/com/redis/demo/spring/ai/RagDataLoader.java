package com.redis.demo.spring.ai;

import java.util.List;
import java.util.zip.GZIPInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.vectorstore.VectorStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class RagDataLoader implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(RagDataLoader.class);

	// 定义关键字数组
	private static final String[] KEYS = { "name", "abv", "ibu", "description" };

	// 获取数据资源
	@Value("classpath:/data/beers.json.gz")
	private Resource data;

	// 获取索引名称
	@Value("${spring.ai.vectorstore.redis.index}")
	private String indexName;

	// 定义VectorStore实例
	private final VectorStore vectorStore;
	
	// 文档数量提供者
	private final DocumentCountProvider documentCountProvider;

	// 构造函数，注入VectorStore实例和文档数量提供者
	public RagDataLoader(VectorStore vectorStore, DocumentCountProvider documentCountProvider) {
		this.vectorStore = vectorStore;
		this.documentCountProvider = documentCountProvider;
	}


	@Override
	public void run(ApplicationArguments args) throws Exception {
		// 检查文档数量，如果已有足够数据则跳过加载
		try {
			int numDocs = documentCountProvider.getDocumentCount(indexName);
			if (numDocs > 20000) {
				logger.info("Embeddings already loaded (found {} documents). Skipping", numDocs);
				return;
			}
			logger.info("Found {} existing documents, proceeding with data loading", numDocs);
		} catch (UnsupportedOperationException e) {
			logger.warn("Document count check not supported for current VectorStore implementation: {}", e.getMessage());
			logger.info("Proceeding with data loading without document count check");
		}
		// 获取数据资源
		Resource file = data;
		// 如果数据资源是.gz格式，则解压
		if (data.getFilename() != null && data.getFilename().endsWith(".gz")) {
			GZIPInputStream inputStream = new GZIPInputStream(data.getInputStream());
			file = new InputStreamResource(inputStream, "beers.json.gz");
		}
		logger.info("Creating Embeddings...");
		// tag::loader[]
		try {
			// Create a JSON reader with fields relevant to our use case
			JsonReader loader = new JsonReader(file, KEYS);
			// Use the autowired VectorStore to insert the documents into Redis
			List<Document> documentList = loader.get();
			vectorStore.add(documentList);
		} catch (RuntimeException e) {
			if (e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			}
			throw e;
		}
		// end::loader[]
		logger.info("Embeddings created.");
	}

}
