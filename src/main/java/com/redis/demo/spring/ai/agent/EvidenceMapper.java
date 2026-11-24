package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Document → Evidence 映射工具。
 * 统一从 Spring AI Document 的 metadata 中提取来源、页码、检索通道和分数字段，
 * 保证 HybridRetrievalTool 与 VectorOnlyRetrievalTool 输出格式一致。
 *
 * 注意：Rerank/RRF 分数不代表模型置信度；若实现未写入分数字段则 score 为空。
 */
final class EvidenceMapper {

    private EvidenceMapper() {
    }

    static List<Evidence> toEvidenceList(List<Document> documents) {
        return documents.stream()
                .map(EvidenceMapper::toEvidence)
                .toList();
    }

    static Evidence toEvidence(Document doc) {
        return new Evidence(
                doc.getId(),
                doc.getText(),
                stringMetadata(doc, "source"),
                intMetadata(doc, "page"),
                stringMetadata(doc, "retrievalSource"),
                doubleMetadata(doc, "rerank_score", "score", "distance", "fusion_score")
        );
    }

    private static String stringMetadata(Document doc, String key) {
        Object value = doc.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private static Integer intMetadata(Document doc, String key) {
        Object value = doc.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double doubleMetadata(Document doc, String... keys) {
        for (String key : keys) {
            Object value = doc.getMetadata().get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null) {
                try {
                    return Double.valueOf(value.toString());
                } catch (NumberFormatException ignored) {
                    // 尝试下一个兼容字段。
                }
            }
        }
        return null;
    }
}
