package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.service.BM25DocumentPersistenceService;
import com.redis.demo.spring.ai.service.BM25SearchService;
import com.redis.demo.spring.ai.service.HybridSearchService;
import com.redis.demo.spring.ai.service.RagService;
import com.redis.demo.spring.ai.service.RerankService;
import io.micrometer.observation.ObservationRegistry;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class RagConfiguration {

    @Value("${spring.ai.vectorstore.pinecone.index-name}")
    private String indexName;

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return restClientBuilder -> restClientBuilder
                .requestFactory(new SimpleClientHttpRequestFactory() {
                    {
                        // 设置连接超时 (毫秒)
                        setConnectTimeout(Duration.ofSeconds(10).toMillisPart());
                        // 设置读取超时 (毫秒) - 这里设置为 60 秒
                        setReadTimeout(Duration.ofSeconds(60).toMillisPart());
                    }
                });
    }

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${spring.ai.ollama.embedding.options.model}") String model) {
        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(baseUrl).build();
        return new OllamaEmbeddingModel(ollamaApi,
                OllamaOptions.builder().model(model).build(),
                ObservationRegistry.create(),
                ModelManagementOptions.builder().timeout(Duration.ofSeconds(30)).build());
    }

    @Bean
    public MilvusServiceClient milvusServiceClient(
            @Value("${spring.ai.vectorstore.milvus.client.uri}") String uri,
            @Value("${spring.ai.vectorstore.milvus.client.token:}") String token) {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withUri(uri)
                .withConnectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withKeepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .withKeepAliveTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withIdleTimeout(30, java.util.concurrent.TimeUnit.SECONDS);
        
        // 如果提供了 token，则设置 token（云服务需要）
        if (token != null && !token.isEmpty()) {
            builder.withToken(token);
        }
        
        ConnectParam connectParam = builder.build();
        return new MilvusServiceClient(connectParam);
    }

    @Bean
    @Primary
    public VectorStore milvusVectorStore(MilvusServiceClient client,
            @Value("${spring.ai.vectorstore.milvus.collection-name}") String collectionName,
            @Value("${spring.ai.vectorstore.milvus.index-type:IVF_FLAT}") String indexTypeStr,
            @Value("${spring.ai.vectorstore.milvus.metric-type:IP}") String metricTypeStr,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.milvus.embeddingDimension:1536}") int dimension,
            @Value("${spring.ai.vectorstore.milvus.database-name:}") String databaseName) {
        IndexType indexType = IndexType.valueOf(indexTypeStr);
        MetricType metricType = MetricType.valueOf(metricTypeStr);
        
        var builder = MilvusVectorStore.builder(client, embeddingModel)
                .collectionName(collectionName)
                .indexType(indexType)
                .metricType(metricType)
                .embeddingDimension(dimension)
                .batchingStrategy(new TokenCountBatchingStrategy())
                .initializeSchema(true);
        
        // 如果配置了数据库名称，则设置数据库名称
        if (databaseName != null && !databaseName.isEmpty()) {
            builder.databaseName(databaseName);
        }
        
        return builder.build();
    }

    /**
     * RerankService Bean - 仅当 rerank.enabled=true 时创建
     */
    @Bean
    @ConditionalOnProperty(name = "rerank.enabled", havingValue = "true")
    public RerankService rerankService(
            @Value("${SILICONFLOW_KEY:${siliconflow.api-key:}}") String apiKey,
            @Value("${rerank.model:BAAI/bge-reranker-v2-m3}") String rerankModel) {
        return new RerankService(apiKey, rerankModel);
    }

    /**
     * BM25DocumentPersistenceService Bean - 仅当 hybrid.search.enabled=true 且 Redis
     * 可用时创建
     * 用于将 BM25 文档数据持久化到 Redis Cloud
     */
    @Bean
    @ConditionalOnProperty(name = "hybrid.search.enabled", havingValue = "true")
    public BM25DocumentPersistenceService bm25DocumentPersistenceService(
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            return new BM25DocumentPersistenceService(redisTemplate);
        }
        return null;
    }

    /**
     * BM25SearchService Bean - 仅当 hybrid.search.enabled=true 时创建
     * Lucene 内存索引，支持 Redis 持久化
     */
    @Bean
    @ConditionalOnProperty(name = "hybrid.search.enabled", havingValue = "true")
    public BM25SearchService bm25SearchService(
            org.springframework.beans.factory.ObjectProvider<BM25DocumentPersistenceService> persistenceServiceProvider) {
        BM25SearchService service = new BM25SearchService();
        BM25DocumentPersistenceService persistenceService = persistenceServiceProvider.getIfAvailable();
        if (persistenceService != null) {
            service.setPersistenceService(persistenceService);
            // 尝试从 Redis 恢复索引
            service.restoreFromPersistence();
        }
        return service;
    }

    /**
     * HybridSearchService Bean - 仅当 hybrid.search.enabled=true 时创建
     * 结合 Lucene BM25 和 Milvus 向量检索，使用 RRF 融合
     */
    @Bean
    @ConditionalOnProperty(name = "hybrid.search.enabled", havingValue = "true")
    public HybridSearchService hybridSearchService(
            VectorStore vectorStore,
            BM25SearchService bm25SearchService) {
        return new HybridSearchService(vectorStore, bm25SearchService);
    }

    /**
     * RagService Bean - 带可选的 RerankService 和 HybridSearchService
     */
    @Bean
    public RagService ragService(ChatModel chatModel, VectorStore vectorStore,
            @Value("${rerank.enabled:false}") boolean rerankEnabled,
            @Value("${hybrid.search.enabled:false}") boolean hybridSearchEnabled,
            org.springframework.beans.factory.ObjectProvider<RerankService> rerankServiceProvider,
            org.springframework.beans.factory.ObjectProvider<HybridSearchService> hybridSearchServiceProvider) {
        RerankService rerankService = rerankEnabled ? rerankServiceProvider.getIfAvailable() : null;
        HybridSearchService hybridSearchService = hybridSearchEnabled
                ? hybridSearchServiceProvider.getIfAvailable()
                : null;
        return new RagService(chatModel, vectorStore, rerankService, hybridSearchService);
    }

}