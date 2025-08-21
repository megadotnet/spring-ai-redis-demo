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
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.HostAndPort;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Connection;
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

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.ai.vectorstore.redis.initialize-schema}")
    private boolean initializeSchema;


    private final RedisConnectionFactory redisConnectionFactory;

    public RagConfiguration(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Bean
    // 定义一个名为jedisPooled的Bean
    public JedisPooled jedisPooled() {
        // 创建一个HostAndPort对象，用于存储Redis的主机和端口
        HostAndPort hostAndPort = new HostAndPort(redisHost, redisPort);
        
        // 判断Redis的密码是否为空
        if (redisPassword != null && !redisPassword.isEmpty()) {
            // 如果不为空，创建一个JedisClientConfig对象，用于存储Redis的密码
            JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                    .password(redisPassword)
                    .build();
            // 返回一个带有密码的JedisPooled对象
            return new JedisPooled(hostAndPort, clientConfig);
        } else {
            // 如果为空，返回一个不带密码的JedisPooled对象
            return new JedisPooled(hostAndPort);
        }
    }

    @Bean
    /**
     * Creates and configures a {@link VectorStore} bean that uses Redis as the backend.
     * This vector store is essential for the RAG pattern, as it stores the document embeddings
     * and allows for efficient similarity searches.
     *
     * @param embeddingModel The {@link EmbeddingModel} to use for creating vector embeddings from text.
     *                       Spring will inject this dependency.
     * @return A fully configured {@link RedisVectorStore} instance, ready to be used by the application.
     */
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled(), embeddingModel)
                .indexName(indexName)
                .prefix(vectorStorePrefix)
                .initializeSchema(initializeSchema)
                .build();
    }


    /// 定义一个RagService的Bean
    @Bean
    public RagService ragService(ChatModel chatModel, VectorStore vectorStore) {
        return new RagService(chatModel, vectorStore);
    }

}