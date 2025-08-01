package com.redis.demo.spring.ai;

import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
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

	// 构造函数，注入VectorStore实例
	public RagDataLoader(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}


	@Override
	public void run(ApplicationArguments args) throws Exception {
		// 获取RedisVectorStore实例
		RedisVectorStore redisVectorStore = (RedisVectorStore) vectorStore;
		// 获取索引信息
		Map<String, Object> indexInfo = redisVectorStore.getJedis().ftInfo(indexName);
		// 获取索引中的文档数量
		int numDocs = Integer.parseInt(String.valueOf(indexInfo.getOrDefault("num_docs", "0")));
		// 如果文档数量大于20000，则跳过
		if (numDocs > 20000) {
			logger.info("Embeddings already loaded. Skipping");
			return;
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
