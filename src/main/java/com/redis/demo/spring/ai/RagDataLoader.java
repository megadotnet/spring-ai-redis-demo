package com.redis.demo.spring.ai;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

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

	//中文description
	public static final String DATA_BEERS_JSON_GZ = "https://qxm.oss-cn-shenzhen.aliyuncs.com/prd/spm/9d3713be-e6c0-4a5f-bb6c-c015dc6bc4f6.png";
	private static final Logger logger = LoggerFactory.getLogger(RagDataLoader.class);

	// 定义关键字数组
	private static final String[] KEYS = { "name", "abv", "ibu", "description" };

	// 获取数据资源
	@Value("classpath:/data/beers.json.gz")
	private Resource data;

	// 获取索引名称
	@Value("${spring.ai.vectorstore.milvus.collection-name}")
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
		String content = document.getFormattedContent();
		int originalLength = content.length();

		String processedContent = truncateTextByTokens(content);

		if (!processedContent.equals(content)) {
			logger.info("Document truncated from {} to {} characters", originalLength, processedContent.length());
		}

		// 确保文档有ID
		Map<String, Object> metadata = new HashMap<>(document.getMetadata());
		if (!metadata.containsKey("id")) {
			if (metadata.containsKey("name")) {
				metadata.put("id", metadata.get("name"));
			} else {
				metadata.put("id", UUID.randomUUID().toString());
			}
		}

		return new Document(processedContent, metadata);
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
			if (numDocs >= 100) {
				logger.info("Embeddings already loaded (found {} documents). Skipping", numDocs);
				return;
			}
			logger.info("Found {} existing documents, proceeding with data loading", numDocs);
		} catch (UnsupportedOperationException e) {
			logger.warn("Document count check not supported for current VectorStore implementation: {}",
					e.getMessage());
			logger.info("Proceeding with data loading without document count check");
		}

		// 检查文件是否有效
		if (file == null || !file.exists()) {
			logger.error("Data file is null or does not exist");
			return;
		}

		// 如果数据资源是.gz格式，则解压
		InputStreamResource inputStreamResource = null;
		if (file.getFilename() != null && file.getFilename().endsWith(".gz")) {
			logger.info("Decompressing GZIP file: {}", file.getFilename());
			GZIPInputStream inputStream = new GZIPInputStream(file.getInputStream());
			inputStreamResource = new InputStreamResource(inputStream, "beers.json");
		} else if (file.getFilename() != null && file.getFilename().endsWith(".zip")) {
			logger.info("Decompressing ZIP file: {}", file.getFilename());
			ZipInputStream zipinputStream = new ZipInputStream(file.getInputStream());
			zipinputStream.getNextEntry();
			inputStreamResource = new InputStreamResource(zipinputStream, "beers.json");
		}

		logger.info("Creating Embeddings...");

		// Create a JSON reader with fields relevant to our use case
		Resource resourceToUse = inputStreamResource != null ? inputStreamResource : file;
		logger.info("Using resource: {}", resourceToUse.getDescription());

		// 检查资源是否可读
		if (!resourceToUse.exists()) {
			logger.error("Resource does not exist: {}", resourceToUse.getDescription());
			return;
		}

		try {
			JsonReader loader = new JsonReader(resourceToUse, KEYS);
			List<Document> documents = loader.get();
			logger.info("Loaded {} documents from JSON", documents.size());
			vectorStore.add(documents);
			logger.info("Added {} documents to vector store", documents.size());
		} catch (Exception e) {
			logger.error("Error processing JSON file: ", e);
			throw e;
		}

		logger.info("Embeddings created.");
	}

	private Resource downloadDataFile() throws IOException {
		Path tempFile = Files.createTempFile("beers-", ".zip");
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