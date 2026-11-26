package com.redis.demo.spring.ai.service;

import org.springframework.ai.document.Document;

/**
 * Rerank 结果封装类
 * 包含原始文档及其与查询的相关性分数
 */
public class RerankResult implements Comparable<RerankResult> {

    private final Document document;
    private final double relevanceScore;

    public RerankResult(Document document, double relevanceScore) {
        this.document = document;
        this.relevanceScore = relevanceScore;
    }

    public Document getDocument() {
        return document;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    @Override
    public int compareTo(RerankResult other) {
        // 降序排列：分数高的排在前面
        return Double.compare(other.relevanceScore, this.relevanceScore);
    }

    @Override
    public String toString() {
        return "RerankResult{" +
                "relevanceScore=" + relevanceScore +
                ", documentText='"
                + (document != null ? document.getText().substring(0, Math.min(50, document.getText().length())) + "..."
                        : "null")
                + '\'' +
                '}';
    }
}
