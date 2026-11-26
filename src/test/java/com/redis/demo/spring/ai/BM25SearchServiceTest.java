package com.redis.demo.spring.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import com.redis.demo.spring.ai.service.BM25SearchService;

/**
 * BM25SearchService 单元测试
 * 测试基于 Lucene 的 BM25 全文检索功能
 */
class BM25SearchServiceTest {

    private BM25SearchService bm25Service;

    @BeforeEach
    void setUp() {
        bm25Service = new BM25SearchService();
    }

    @Test
    void testIndexDocuments_成功建立索引() {
        // 准备测试数据
        List<Document> documents = Arrays.asList(
                new Document("Spring AI 是一个强大的人工智能框架"),
                new Document("Milvus 是高性能向量数据库"),
                new Document("BM25 算法用于文本相关性评分"));

        // 执行索引
        bm25Service.indexDocuments(documents);

        // 验证
        assertTrue(bm25Service.isIndexBuilt(), "索引应该已构建");
        assertEquals(3, bm25Service.getDocumentCount(), "应该有3个文档");
    }

    @Test
    void testSearch_关键词匹配() {
        // 准备测试数据
        List<Document> documents = Arrays.asList(
                new Document("Spring AI 是一个用于构建 AI 应用的框架"),
                new Document("Milvus 是一个开源向量数据库"),
                new Document("混合检索结合了 BM25 和向量搜索"),
                new Document("RRF 算法用于融合多个检索结果"));
        bm25Service.indexDocuments(documents);

        // 执行搜索
        List<BM25SearchService.ScoredDocument> results = bm25Service.search("Spring AI 框架", 3);

        // 验证
        assertFalse(results.isEmpty(), "应该返回结果");
        assertTrue(results.get(0).getDocument().getText().contains("Spring AI"),
                "第一个结果应该包含 Spring AI");
    }

    @Test
    void testSearch_中文分词搜索() {
        // 准备测试数据
        List<Document> documents = Arrays.asList(
                new Document("机器学习和深度学习是人工智能的核心技术"),
                new Document("自然语言处理用于理解和生成文本"),
                new Document("检索增强生成提高了大模型的准确性"));
        bm25Service.indexDocuments(documents);

        // 执行搜索
        List<BM25SearchService.ScoredDocument> results = bm25Service.search("人工智能", 2);

        // 验证
        assertFalse(results.isEmpty(), "应该返回中文搜索结果");
    }

    @Test
    void testSearch_空索引返回空结果() {
        // 在未建立索引时搜索
        List<BM25SearchService.ScoredDocument> results = bm25Service.search("test query", 5);

        // 验证
        assertTrue(results.isEmpty(), "空索引应该返回空结果");
        assertFalse(bm25Service.isIndexBuilt(), "索引应该未构建");
    }

    @Test
    void testSearch_空查询返回空结果() {
        // 建立索引
        bm25Service.indexDocuments(Arrays.asList(new Document("测试文档")));

        // 使用空查询搜索
        List<BM25SearchService.ScoredDocument> emptyResults = bm25Service.search("", 5);
        List<BM25SearchService.ScoredDocument> nullResults = bm25Service.search(null, 5);

        // 验证
        assertTrue(emptyResults.isEmpty(), "空查询应该返回空结果");
        assertTrue(nullResults.isEmpty(), "null 查询应该返回空结果");
    }

    @Test
    void testClearIndex_清空索引() {
        // 建立索引
        bm25Service.indexDocuments(Arrays.asList(
                new Document("文档1"),
                new Document("文档2")));
        assertTrue(bm25Service.isIndexBuilt());

        // 清空索引
        bm25Service.clearIndex();

        // 验证
        assertFalse(bm25Service.isIndexBuilt(), "索引应该已清空");
        assertEquals(0, bm25Service.getDocumentCount(), "文档数量应该为0");
    }

    @Test
    void testAddDocuments_追加文档() {
        // 初始索引
        bm25Service.indexDocuments(Arrays.asList(new Document("初始文档")));
        assertEquals(1, bm25Service.getDocumentCount());

        // 追加文档
        bm25Service.addDocuments(Arrays.asList(
                new Document("追加文档1"),
                new Document("追加文档2")));

        // 验证
        assertEquals(3, bm25Service.getDocumentCount(), "应该有3个文档");
    }

    @Test
    void testScoredDocument_排名和分数() {
        // 准备测试数据
        List<Document> documents = Arrays.asList(
                new Document("Java 编程语言最佳实践"),
                new Document("Python 是数据科学的首选语言"),
                new Document("Java 和 Spring Boot 开发指南"),
                new Document("Kotlin 是现代 JVM 语言"));
        bm25Service.indexDocuments(documents);

        // 执行搜索
        List<BM25SearchService.ScoredDocument> results = bm25Service.search("Java", 3);

        // 验证排名
        for (int i = 0; i < results.size(); i++) {
            assertEquals(i + 1, results.get(i).getRank(), "排名应该从1开始递增");
            assertTrue(results.get(i).getScore() > 0, "分数应该大于0");
        }
    }
}
