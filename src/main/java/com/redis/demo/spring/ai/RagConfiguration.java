package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.service.RagService;
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
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class RagConfiguration {

    @Value("${spring.ai.vectorstore.pinecone.index-name}")
    private String indexName;

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return restClientBuilder -> restClientBuilder
                .requestFactory(new SimpleClientHttpRequestFactory() {{
                    // 设置连接超时 (毫秒)
                    setConnectTimeout(Duration.ofSeconds(10).toMillisPart());
                    // 设置读取超时 (毫秒) - 这里设置为 60 秒，覆盖您的 25 秒需求
                    setReadTimeout(Duration.ofSeconds(60).toMillisPart());
                }});
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
    public MilvusServiceClient milvusServiceClient(@Value("${spring.ai.vectorstore.milvus.client.host}") String host) {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(19530)
                .withConnectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withKeepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .withKeepAliveTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withIdleTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
                
        return new MilvusServiceClient(connectParam);
    }

    @Bean
    @Primary
    public VectorStore milvusVectorStore(MilvusServiceClient client,
                                        @Value("${spring.ai.vectorstore.milvus.collection-name}") String collectionName,
                                        EmbeddingModel embeddingModel,
                                        @Value("${spring.ai.vectorstore.milvus.embeddingDimension:1536}") int dimension) {
        return MilvusVectorStore.builder(client, embeddingModel)
                .collectionName(collectionName)
                .databaseName("default")
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .embeddingDimension(dimension)
                .batchingStrategy(new TokenCountBatchingStrategy())
                //.autoId( true)
                .initializeSchema(true)  // 自动初始化schema
                .build();
    }

    /// 定义一个RagService的Bean
    @Bean
    public RagService ragService(ChatModel chatModel, VectorStore vectorStore) {
        return new RagService(chatModel, vectorStore);
    }

}