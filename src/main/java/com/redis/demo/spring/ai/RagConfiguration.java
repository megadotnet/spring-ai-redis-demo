package com.redis.demo.spring.ai;

import io.micrometer.observation.ObservationRegistry;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class RagConfiguration {

    @Value("${spring.ai.vectorstore.pinecone.index-name}")
    private String indexName;
    
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
    public VectorStore milvusVectorStore(@Value("${spring.ai.vectorstore.milvus.client.host}") String host,
                                        @Value("${spring.ai.vectorstore.milvus.collection-name}") String collectionName,
                                        EmbeddingModel embeddingModel) {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(19530)
                .withConnectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withKeepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .withKeepAliveTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withIdleTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
                
        MilvusServiceClient client = new MilvusServiceClient(connectParam);
        return MilvusVectorStore.builder(client, embeddingModel).build();
    }

    /// 定义一个RagService的Bean
    @Bean
    public RagService ragService(ChatModel chatModel, VectorStore vectorStore) {
        return new RagService(chatModel, vectorStore);
    }

}