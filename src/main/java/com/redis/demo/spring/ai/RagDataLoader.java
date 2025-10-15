package com.redis.demo.spring.ai;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

@Component
public class RagDataLoader implements ApplicationRunner {

	public static final String DATA_BEERS_JSON_GZ = "https://gh.llkk.cc/https://github.com/megadotnet/spring-ai-redis-demo/raw/refs/heads/main/src/main/resources/data/beers.json.gz";
	private static final Logger logger = LoggerFactory.getLogger(RagDataLoader.class);

	// 定义关键字数组
	private static final String[] KEYS = { "name", "abv", "ibu", "description" };

	// 获取数据资源
	@Value("classpath:/data/beers.json.gz")
	private Resource data;

	// 获取索引名称
	@Value("${spring.ai.vectorstore.pinecone.index-name}")
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

	// 在RagDataLoader类中添加批次大小常量
	private static final int BATCH_SIZE = 32;

	// 在 RagDataLoader 类中添加文本处理方法
// 在 RagDataLoader 类中添加以下方法
	private static final int MAX_TOKENS = 512;
	private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

	// 添加处理文档内容的方法
	private Document processDocumentContent(Document document) {
		String content = document.toString();
		int originalLength = content.length();

		String processedContent = truncateTextByTokens(content);

		if (!processedContent.equals(content)) {
			logger.info("Document truncated from {} to {} characters", originalLength, processedContent.length());
			return new Document(processedContent, document.getMetadata());
		}

		return document;
	}


	private String truncateTextByTokens(String text) {
		// 更保守的限制 - 使用更低的字符到token比率
		int maxChars = 1500; // 大约375 tokens (基于1:4比率)

		if (text.length() > maxChars) {
			String truncatedText = text.substring(0, maxChars);
			// 在句子边界截断
			int lastPeriod = truncatedText.lastIndexOf('.');
			int lastSpace = truncatedText.lastIndexOf(' ');

			// 优先在句号后截断，其次在空格处截断
			if (lastPeriod > 0) {
				truncatedText = truncatedText.substring(0, lastPeriod + 1);
			} else if (lastSpace > 0) {
				truncatedText = truncatedText.substring(0, lastSpace);
			}

			logger.debug("Truncated text from {} to {} characters", text.length(), truncatedText.length());
			return truncatedText;
		}
		return text;
	}



	@Override
	public void run(ApplicationArguments args) throws Exception {

		// 获取数据资源
		Resource file = data;

		if (!file.exists()) {
			logger.info("Local data file not found, downloading from URL...");
			file = downloadDataFile();
		}

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

		// 如果数据资源是.gz格式，则解压
		if (file.getFilename() != null && file.getFilename().endsWith(".gz")) {
			GZIPInputStream inputStream = new GZIPInputStream(file.getInputStream());
			file = new InputStreamResource(inputStream, "beers.json.gz");
		}
		logger.info("Creating Embeddings...");
// 替换 JsonReader 处理逻辑
		try {
			// 读取原始 JSON 数据
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode jsonNode = objectMapper.readTree(file.getInputStream());

			List<Document> documentList = new ArrayList<>();

			if (jsonNode.isArray()) {
				for (JsonNode node : jsonNode) {
					String content = buildContentFromJsonNode(node);
					Map<String, Object> metadata = buildMetadataFromJsonNode(node);
					Document document = new Document(content, metadata);
					documentList.add(document);
				}
			}

			// 对文档进行预处理，确保token数量符合要求
			List<Document> processedDocuments = documentList.stream()
					.map(this::processDocumentContent)
					.collect(Collectors.toList());

			// 分批处理文档
			for (int i = 0; i < processedDocuments.size(); i += BATCH_SIZE) {
				int endIndex = Math.min(i + BATCH_SIZE, processedDocuments.size());
				List<Document> batch = processedDocuments.subList(i, endIndex);
				vectorStore.add(batch);
				logger.info("Processed batch {}/{}", (i / BATCH_SIZE) + 1,
						(processedDocuments.size() + BATCH_SIZE - 1) / BATCH_SIZE);
			}
		}
		catch (RuntimeException e) {
			if (e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			}
			throw e;
		}
		// end::loader[]
		logger.info("Embeddings created.");
	}

	private String buildContentFromJsonNode(JsonNode node) {
		StringBuilder content = new StringBuilder();

		for (String key : KEYS) {
			if (node.has(key) && !node.get(key).isNull()) {
				String value = node.get(key).asText();
				// 限制每个字段的长度
				if (value.length() > 500) {
					value = value.substring(0, 500);
				}
				content.append(key).append(": ").append(value).append("\n");
			}
		}

		return content.toString();
	}

	private Map<String, Object> buildMetadataFromJsonNode(JsonNode node) {
		Map<String, Object> metadata = new HashMap<>();

		// 可以添加额外的元数据字段
		if (node.has("name")) {
			metadata.put("name", node.get("name").asText());
		}

		return metadata;
	}


	private Resource downloadDataFile() throws IOException {
		Path tempFile = Files.createTempFile("beers-", ".json.gz");
		logger.info("Downloading data file from: {} to: {}", DATA_BEERS_JSON_GZ, tempFile);

		URL downloadUrl = new URL(DATA_BEERS_JSON_GZ);
		URLConnection connection = downloadUrl.openConnection();
		connection.setConnectTimeout(10000); // 10s
		connection.setReadTimeout(60000); // 60s

		try (InputStream in = connection.getInputStream()) {
			Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}

		logger.info("Data file downloaded successfully.");
		return new UrlResource(tempFile.toUri());
	}

}
