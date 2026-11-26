package com.redis.demo.spring.ai.model;

/**
 * 检索证据 DTO。
 * documentId 必须尽量稳定可回查；metadata 缺失时其余字段允许为空。
 *
 * @param documentId      稳定的文档/分块 ID
 * @param content         证据文本内容
 * @param source          来源文件名或 URI
 * @param page            页码（如可用）
 * @param retrievalSource 检索来源标识（dense / bm25 / hybrid 等）
 * @param score           相关度分数（Rerank 或融合分数；不代表模型置信度）
 */
public record Evidence(
        String documentId,
        String content,
        String source,
        Integer page,
        String retrievalSource,
        Double score
) {
}
