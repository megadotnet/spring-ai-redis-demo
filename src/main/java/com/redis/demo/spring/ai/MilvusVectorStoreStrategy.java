package com.redis.demo.spring.ai;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Component;

/**
 * Milvus VectorStore策略实现
 */
@Component
public class MilvusVectorStoreStrategy implements VectorStoreStrategy {
    
    @Override
    public boolean supports(VectorStore vectorStore) {
        return vectorStore instanceof MilvusVectorStore;
    }
    
    @Override
    public int getDocumentCount(VectorStore vectorStore, String indexName) {
        // Milvus VectorStore不直接支持文档计数功能
        // 返回-1表示该操作不支持
        if (!supports(vectorStore)) {
            throw new IllegalArgumentException("不支持的VectorStore类型: " + vectorStore.getClass().getName());
        }
        
        // 对于Milvus，我们暂时返回0表示需要使用其他方式计算
        return 0;
    }
}