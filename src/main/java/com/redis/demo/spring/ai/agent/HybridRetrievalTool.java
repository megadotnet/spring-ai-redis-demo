package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;
import com.redis.demo.spring.ai.service.HybridSearchService;
import com.redis.demo.spring.ai.service.RerankService;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 混合检索工具：包装现有 HybridSearchService（Milvus + BM25 + RRF）与可选的 RerankService。
 * Agent 层通过统一 RetrievalTool 接口使用检索能力，不依赖 Lucene/Milvus 细节。
 *
 * 注意：本类不加 @Component，由 RagConfiguration 按 hybrid.search.enabled 装配，
 * 保证 hybrid 关闭时回退到 VectorOnlyRetrievalTool，应用启动不会因缺少工具 Bean 失败。
 */
public class HybridRetrievalTool implements RetrievalTool {

    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;

    public HybridRetrievalTool(
            HybridSearchService hybridSearchService,
            RerankService rerankService) {
        this.hybridSearchService = hybridSearchService;
        // rerank.enabled=false 时 RagConfiguration 不创建 RerankService Bean，此处允许为 null。
        this.rerankService = rerankService;
    }

    @Override
    public List<Evidence> retrieve(String query, int topK) {
        List<Document> candidates = hybridSearchService.hybridSearch(query, topK);

        List<Document> selected = candidates;
        if (rerankService != null && !candidates.isEmpty()) {
            // RerankService 内部失败时会返回原始顺序，属于结构化降级。
            selected = rerankService.rerank(query, candidates, topK);
        }

        return EvidenceMapper.toEvidenceList(selected);
    }
}
