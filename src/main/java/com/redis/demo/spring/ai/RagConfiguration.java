package com.redis.demo.spring.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import redis.clients.jedis.JedisPooled;

import java.time.Duration;

@Configuration
public class RagConfiguration {

    @Value("${spring.ai.vectorstore.redis.index}")
    private String indexName;

    @Value("${spring.ai.vectorstore.redis.prefix}")
    private String vectorStorePrefix;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.ai.vectorstore.redis.initialize-schema}")
    private boolean initializeSchema;

    private final RedisConnectionFactory redisConnectionFactory;

    public RagConfiguration(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

   // @Primary
   // @Bean
   // EmbeddingModel embeddingModel() {
   //     return new TransformersEmbeddingModel(MetadataMode.EMBED);
   // }

   //@Bean
   //ChatModel chatModel() {
   //    OpenAiChatOptions options = OpenAiChatOptions.builder()
   //            .model("deepseek-ai/DeepSeek-V3")
   //            .build();
   //    return OpenAiChatModel.builder()
   //            .openAiApi(openAiApi())
   //            .defaultOptions(options)
   //            .build();
   //}

    @Bean
    public JedisPooled jedisPooled() {
        return new JedisPooled(redisHost, redisPort);
    }

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled(), embeddingModel)
                .indexName(indexName)
                .prefix(vectorStorePrefix)
                .initializeSchema(initializeSchema)
                .build();
    }

    //@Bean
    //OpenAiApi openAiApi() {
    //    return OpenAiApi.builder()
    //            .baseUrl("https://api.siliconflow.cn/v1")
    //            .apiKey(System.getenv("OPENAPI_KEY"))
    //            .build();
    //}
//


    @Bean
    public RagService ragService(ChatModel chatModel, VectorStore vectorStore) {
        return new RagService(chatModel, vectorStore);
    }

}