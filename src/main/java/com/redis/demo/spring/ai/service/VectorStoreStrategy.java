package com.redis.demo.spring.ai.service;

import org.springframework.ai.vectorstore.VectorStore;

/**
 * VectorStore策略接口
 * 提供不同VectorStore实现的特定操作
 */
public interface VectorStoreStrategy {
    
    /**
     * 检查是否支持该VectorStore类型
     * @param vectorStore VectorStore实例
     * @return 是否支持
     */
    boolean supports(VectorStore vectorStore);
    
    /**
     * 获取文档数量
     * @param vectorStore VectorStore实例
     * @param indexName 索引名称
     * @return 文档数量
     */
    int getDocumentCount(VectorStore vectorStore, String indexName);
}