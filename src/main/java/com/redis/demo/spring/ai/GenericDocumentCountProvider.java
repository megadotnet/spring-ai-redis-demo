package com.redis.demo.spring.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

/**
 * 通用文档数量提供者实现
 * 通过相似性搜索来估算文档数量
 */
public class GenericDocumentCountProvider implements DocumentCountProvider {
    
    private final VectorStore vectorStore;
    
    public GenericDocumentCountProvider(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    
    @Override
    public int getDocumentCount(String indexName) {
        // 使用相似性搜索来估算文档总数
        try {
            // 执行一个简单的查询来获取一些文档
            List<Document> documentList = vectorStore.similaritySearch(
                SearchRequest.builder().query(" ").topK(1000).build());
            
            if (documentList != null) {
                return documentList.size();
            } else {
                return 0;
            }
        } catch (Exception e) {
            throw new UnsupportedOperationException(
                "当前VectorStore实现不支持文档数量查询: " + e.getMessage());
        }
    }
}