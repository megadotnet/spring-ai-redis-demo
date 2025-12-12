package com.redis.demo.spring.ai;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * 通用VectorStore策略实现
 */
public class GenericVectorStoreStrategy implements VectorStoreStrategy {
    
    @Override
    public boolean supports(VectorStore vectorStore) {
        // 默认实现，可以支持任何类型的VectorStore
        return true;
    }
    
    @Override
    public int getDocumentCount(VectorStore vectorStore, String indexName) {
        // 使用通用方法获取文档数量
        try {
            GenericDocumentCountProvider provider = new GenericDocumentCountProvider(vectorStore);
            return provider.getDocumentCount(indexName);
        } catch (Exception e) {
            return -1; // 表示无法确定文档数量
        }
    }
}