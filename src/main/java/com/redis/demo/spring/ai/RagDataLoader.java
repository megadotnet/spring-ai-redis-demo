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

/**
 * 应用启动后用于加载示例数据并生成向量嵌入。
 *
 * 逻辑：
 * - 检查索引中的文档数量，若已存在大量数据则跳过
 * - 读取压缩或未压缩的 JSON 数据
 * - 使用 JsonReader 解析并写入向量存储
 */
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
    /**
     * 构造函数，注入向量存储。
     *
     * @param vectorStore 向量存储实现
     */
    public RagDataLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }


    @Override
    /**
     * 应用启动后执行的数据加载流程。
     *
     * @param args 应用参数
     * @throws Exception 发生 IO 错误时抛出
     */
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
