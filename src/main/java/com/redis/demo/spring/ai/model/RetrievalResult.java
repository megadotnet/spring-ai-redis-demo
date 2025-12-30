package com.redis.demo.spring.ai.model;

import java.util.Map;

/**
 * 用于 RAG 测试的检索结果 DTO
 * 对应 Python 脚本中的 response.json() 结构
 */
public record RetrievalResult(
        String id,              // 文档的唯一 ID (Milvus 中的 doc_id)
        String content,         // 文档的文本内容
        Double score,           // 相似度得分 (Distance/Similarity)
        Map<String, Object> metadata // 元数据 (如文件名、页码等)
) {
    // 可以在这里添加静态工厂方法，简化创建过程
    public static RetrievalResult from(String id, String content, Double score, Map<String, Object> metadata) {
        return new RetrievalResult(id, content, score, metadata);
    }
}