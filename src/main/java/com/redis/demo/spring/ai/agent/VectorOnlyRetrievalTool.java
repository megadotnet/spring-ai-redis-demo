package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

/**
 * 纯向量检索工具：当 hybrid.search.enabled=false（HybridSearchService Bean 不存在）时
 * 作为 RetrievalTool 的降级实现，保证 Agent 链路始终有可用检索工具。
 * 可选接入 RerankService；单路 Milvus 失败时返回空列表并记录错误，不使整条链失败。
 */
public class VectorOnlyRetrievalTool implements RetrievalTool {

    private static final Logger logger = LoggerFactory.getLogger(VectorOnlyRetrievalTool.class);

    private final VectorStore vectorStore;
    private final RerankHolder rerankHolder;

    /**
     * Rerank 持有器：独立出来以便在无 RerankService Bean 时传 null。
     */
    public record RerankHolder(com.redis.demo.spring.ai.service.RerankService rerankService) {
        boolean available() {
            return rerankService != null;
        }
    }

    public VectorOnlyRetrievalTool(VectorStore vectorStore, RerankHolder rerankHolder) {
        this.vectorStore = vectorStore;
        this.rerankHolder = rerankHolder;
    }

    @Override
    public List<Evidence> retrieve(String query, int topK) {
        List<Document> candidates;
        try {
            candidates = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .build());
        } catch (Exception ex) {
            // 结构化降级：向量服务故障时返回空结果，由 Runtime 决定拒答或重试策略。
            logger.error("Vector retrieval failed for query '{}': {}", query, ex.getMessage(), ex);
            return List.of();
        }

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<Document> selected = candidates;
        if (rerankHolder.available()) {
            selected = rerankHolder.rerankService().rerank(query, candidates, topK);
        }

        return EvidenceMapper.toEvidenceList(selected);
    }
}
