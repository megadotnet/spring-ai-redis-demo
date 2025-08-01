package com.redis.demo.spring.ai;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Redis VectorStore策略实现
 */
@Component
public class RedisVectorStoreStrategy implements VectorStoreStrategy {
    
    @Override
    public boolean supports(VectorStore vectorStore) {
        return vectorStore instanceof RedisVectorStore;
    }
    
    @Override
    public int getDocumentCount(VectorStore vectorStore, String indexName) {
        if (!supports(vectorStore)) {
            throw new IllegalArgumentException("不支持的VectorStore类型: " + vectorStore.getClass().getName());
        }
        
        RedisVectorStore redisVectorStore = (RedisVectorStore) vectorStore;
        Map<String, Object> indexInfo = redisVectorStore.getJedis().ftInfo(indexName);
        return Integer.parseInt(String.valueOf(indexInfo.getOrDefault("num_docs", "0")));
    }
}