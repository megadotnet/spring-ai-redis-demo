package com.redis.demo.spring.ai.service;

/**
 * 提供文档数量查询功能的接口
 * 用于抽象不同VectorStore实现的文档数量获取逻辑
 */
public interface DocumentCountProvider {
    
    /**
     * 获取指定索引中的文档数量
     * @param indexName 索引名称
     * @return 文档数量
     */
    int getDocumentCount(String indexName);
}