package com.redis.demo.spring.ai;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Component;

/**
 * Milvus专用文档数量提供者
 */
@Component
public class MilvusDocumentCountProvider implements DocumentCountProvider {
    
    private final VectorStore vectorStore;
    
    public MilvusDocumentCountProvider(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    
    @Override
    public int getDocumentCount(String indexName) {
        // Milvus目前不支持直接统计文档数量
        // 返回0表示无法获取准确的数量
        return 0;
    }
}