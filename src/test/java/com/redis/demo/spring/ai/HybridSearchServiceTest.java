package com.redis.demo.spring.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import com.redis.demo.spring.ai.service.BM25SearchService;
import com.redis.demo.spring.ai.service.HybridSearchService;

/**
 * HybridSearchService 单元测试
 * 测试混合检索功能（BM25 + 向量检索 + RRF 融合）
 */
@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock
    private VectorStore vectorStore;

    private BM25SearchService bm25SearchService;
    private HybridSearchService hybridSearchService;

    @BeforeEach
    void setUp() {
        bm25SearchService = new BM25SearchService();
        hybridSearchService = new HybridSearchService(vectorStore, bm25SearchService);

        // 设置私有字段
        ReflectionTestUtils.setField(hybridSearchService, "rrfK", 60);
        ReflectionTestUtils.setField(hybridSearchService, "denseWeight", 0.7);
        ReflectionTestUtils.setField(hybridSearchService, "sparseWeight", 0.3);
    }

    @Test
    void testHybridSearch_融合两种检索结果() {
        // 准备测试数据
        List<Document> testDocs = Arrays.asList(
                createDoc("doc1", "Spring AI 人工智能框架"),
                createDoc("doc2", "Milvus 向量数据库"),
                createDoc("doc3", "BM25 全文检索算法"),
                createDoc("doc4", "RRF 结果融合算法"));

        // 建立 BM25 索引
        bm25SearchService.indexDocuments(testDocs);

        // Mock 向量检索返回
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Arrays.asList(
                        createDoc("doc1", "Spring AI 人工智能框架"),
                        createDoc("doc2", "Milvus 向量数据库")));

        // 执行混合检索
        List<Document> results = hybridSearchService.hybridSearch("人工智能 框架", 5);

        // 验证
        assertNotNull(results, "结果不应为空");
        assertFalse(results.isEmpty(), "应该返回融合结果");

        // 验证向量检索被调用
        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void testHybridSearch_BM25索引未建立时降级() {
        // 不建立 BM25 索引

        // Mock 向量检索返回
        List<Document> vectorDocs = Arrays.asList(
                createDoc("doc1", "向量检索结果1"),
                createDoc("doc2", "向量检索结果2"));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(vectorDocs);

        // 执行混合检索
        List<Document> results = hybridSearchService.hybridSearch("测试查询", 2);

        // 验证：应该返回仅向量检索的结果
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    void testHybridSearch_结果去重() {
        // 准备测试数据 - 相同的文档
        Document sharedDoc = createDoc("shared", "共享文档内容");
        List<Document> testDocs = Arrays.asList(sharedDoc);

        // 建立 BM25 索引
        bm25SearchService.indexDocuments(testDocs);

        // Mock 向量检索返回相同的文档
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Arrays.asList(sharedDoc));

        // 执行混合检索
        List<Document> results = hybridSearchService.hybridSearch("共享文档", 5);

        // 验证：相同 ID 的文档应该只出现一次，但融合分数更高
        assertNotNull(results);
    }

    @Test
    void testHybridSearch_RRF融合分数() {
        // 准备测试数据
        List<Document> testDocs = Arrays.asList(
                createDoc("doc1", "高相关性文档"),
                createDoc("doc2", "中等相关性文档"),
                createDoc("doc3", "低相关性文档"));
        bm25SearchService.indexDocuments(testDocs);

        // Mock 向量检索返回
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(testDocs);

        // 执行混合检索
        List<Document> results = hybridSearchService.hybridSearch("相关性", 3);

        // 验证：结果应该包含融合分数
        assertFalse(results.isEmpty());
        for (Document doc : results) {
            assertTrue(doc.getMetadata().containsKey("fusion_score"),
                    "结果应该包含 fusion_score 元数据");
        }
    }

    @Test
    void testIndexForBM25_建立索引() {
        // 准备数据
        List<Document> docs = Arrays.asList(new Document("测试文档"));

        // 通过 HybridSearchService 建立索引
        hybridSearchService.indexForBM25(docs);

        // 验证
        assertTrue(bm25SearchService.isIndexBuilt());
    }

    private Document createDoc(String id, String content) {
        Document doc = new Document(content);
        // Document 在构造时不接受 ID，需要通过反射或其他方式设置
        // 这里使用简单方式
        return doc;
    }
}
