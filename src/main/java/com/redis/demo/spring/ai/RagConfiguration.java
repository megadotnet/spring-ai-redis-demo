package com.redis.demo.spring.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.redis.RedisVectorStore;
import org.springframework.ai.redis.RedisVectorStore.RedisVectorStoreConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
public class RagConfiguration {

    @Value("${spring.ai.vectorstore.redis.index}")
    private String indexName;

    @Value("${spring.ai.vectorstore.redis.prefix}")
    private String vectorStorePrefix;

    @Value("${spring.ai.vectorstore.redis.dimensions}")
    private int noOfDimensions;

    private final RedisConnectionFactory redisConnectionFactory;

    public RagConfiguration(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Primary
    @Bean
    EmbeddingModel embeddingModel() {
        return new TransformersEmbeddingModel(MetadataMode.EMBED);
    }

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return new org.springframework.ai.redis.RedisVectorStore(redisConnectionFactory, embeddingModel, org.springframework.ai.redis.RedisVectorStore.RedisVectorStoreConfig.builder()
                .withIndexName(indexName)
                .withPrefix(vectorStorePrefix)
                .withNoOfDimensions(noOfDimensions)
                .build());
    }

    @Bean
    OpenAiApi openAiApi() {
        return OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
    }

    @Bean
    ChatModel chatModel(OpenAiApi openAiApi) {
        return new OpenAiChatModel(openAiApi, OpenAiChatOptions.builder().build());
    }

    @Bean
    RagService ragService(ChatModel chatModel, VectorStore vectorStore) {
        return new RagService(chatModel, vectorStore);
    }

}
