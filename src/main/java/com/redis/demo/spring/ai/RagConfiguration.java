package com.redis.demo.spring.ai;

import io.micrometer.observation.ObservationRegistry;
import io.pinecone.clients.Pinecone;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.HostAndPort;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;

import java.time.Duration;

@Configuration
public class RagConfiguration {

    @Value("${spring.ai.vectorstore.pinecone.index-name}")
    private String indexName;

    @Bean
    public Pinecone pineconeClient(@Value("${spring.ai.vectorstore.pinecone.apiKey}") String apiKey) {
        return new Pinecone.Builder(apiKey).build();
    }

    private final RedisConnectionFactory redisConnectionFactory;

    public RagConfiguration(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
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

    /// 定义一个RagService的Bean
    @Bean
    public RagService ragService(ChatModel chatModel, VectorStore vectorStore) {
        return new RagService(chatModel, vectorStore);
    }

}