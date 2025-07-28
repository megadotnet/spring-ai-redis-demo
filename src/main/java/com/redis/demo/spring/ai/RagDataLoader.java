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

	private static final String[] KEYS = { "name", "abv", "ibu", "description" };

	@Value("classpath:/data/beers.json.gz")
	private Resource data;

	@Value("${spring.ai.vectorstore.redis.index}")
	private String indexName;

	private final VectorStore vectorStore;

	public RagDataLoader(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}


	@Override
	public void run(ApplicationArguments args) throws Exception {
		RedisVectorStore redisVectorStore = (RedisVectorStore) vectorStore;
		Map<String, Object> indexInfo = redisVectorStore.getJedis().ftInfo(indexName);
		int numDocs = Integer.parseInt(String.valueOf(indexInfo.getOrDefault("num_docs", "0")));
		if (numDocs > 20000) {
			logger.info("Embeddings already loaded. Skipping");
			return;
		}
		Resource file = data;
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
