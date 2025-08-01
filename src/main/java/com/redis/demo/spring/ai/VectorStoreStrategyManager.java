package com.redis.demo.spring.ai;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * VectorStore策略管理器
 * 根据VectorStore类型选择合适的策略
 */
@Component
public class VectorStoreStrategyManager {
    
    private final List<VectorStoreStrategy> strategies;
    
    public VectorStoreStrategyManager(List<VectorStoreStrategy> strategies) {
        this.strategies = strategies;
    }
    
    /**
     * 获取文档数量
     * @param vectorStore VectorStore实例
     * @param indexName 索引名称
     * @return 文档数量
     * @throws UnsupportedOperationException 如果没有找到支持的策略
     */
    public int getDocumentCount(VectorStore vectorStore, String indexName) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(vectorStore))
                .findFirst()
                .map(strategy -> strategy.getDocumentCount(vectorStore, indexName))
                .orElseThrow(() -> new UnsupportedOperationException(
                    "没有找到支持 " + vectorStore.getClass().getName() + " 的策略实现"));
    }
}