package com.redis.demo.spring.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPooled;

/**
 * RagDataLoader 集成测试
 * 使用 TestContainers 启动真实的 Redis 容器进行集成测试
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("RagDataLoader 集成测试")
class RagDataLoaderIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis/redis-stack:latest"))
            .withExposedPorts(6379);

    @Autowired
    private RagDataLoader ragDataLoader;

    @MockBean
    private RedisVectorStore redisVectorStore;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.ai.vectorstore.redis.uri", 
            () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        registry.add("spring.ai.vectorstore.redis.index", () -> "test-beer-index");
    }

    @BeforeEach
    void setUp() {
        // 清理测试数据
        try (Jedis jedis = new Jedis(redis.getHost(), redis.getMappedPort(6379))) {
            jedis.flushAll();
        }
    }

    @DisplayName("集成测试：验证 Redis 连接和基本操作")
    void shouldConnectToRedisAndPerformBasicOperations() {
        // Given
        String testKey = "test:key";
        String testValue = "test:value";

        // When
        try (Jedis jedis = new Jedis(redis.getHost(), redis.getMappedPort(6379))) {
            jedis.set(testKey, testValue);
            String retrievedValue = jedis.get(testKey);

            // Then
            assertThat(retrievedValue).isEqualTo(testValue);
        }
    }

    @DisplayName("集成测试：验证 Redis 索引信息查询")
    void shouldQueryRedisIndexInfo() {
        // Given
        String indexName = "test-beer-index";

        // When & Then
        try (JedisPooled jedis = new JedisPooled(redis.getHost(), redis.getMappedPort(6379))) {
            // 尝试获取索引信息，如果索引不存在会抛出异常
            try {
                Map<String, Object> indexInfo = jedis.ftInfo(indexName);
                assertThat(indexInfo).isNotNull();
            } catch (Exception e) {
                // 索引不存在是正常的，因为我们还没有创建
                assertThat(e.getMessage()).contains("Unknown index name");
            }
        }
    }

    @DisplayName("集成测试：验证应用上下文加载")
    void shouldLoadApplicationContext() {
        // Then
        assertThat(ragDataLoader).isNotNull();
        assertThat(redisVectorStore).isNotNull();
    }
}