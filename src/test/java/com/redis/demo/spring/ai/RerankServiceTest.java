package com.redis.demo.spring.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import com.redis.demo.spring.ai.service.RerankResult;
import com.redis.demo.spring.ai.service.RerankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

/**
 * RerankService 单元测试类
 * 测试 Rerank 精排服务的核心功能
 */
@ExtendWith(MockitoExtension.class)
class RerankServiceTest {

    private RerankService rerankService;

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String RERANK_MODEL = "qllama/bge-reranker-v2-m3";

    @BeforeEach
    void setUp() {
        rerankService = new RerankService(OLLAMA_BASE_URL, RERANK_MODEL);
    }

    @Test
    void testRerank_空文档列表返回空列表() {
        // 执行测试
        List<Document> result = rerankService.rerank("query", Arrays.asList(), 5);

        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.isEmpty(), "空输入应返回空列表");
    }

    @Test
    void testRerank_null文档列表返回空列表() {
        // 执行测试
        List<Document> result = rerankService.rerank("query", null, 5);

        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.isEmpty(), "null输入应返回空列表");
    }

    @Test
    void testRerankResult_比较功能() {
        // 测试 RerankResult 的比较功能
        Document doc1 = new Document("文档1");
        Document doc2 = new Document("文档2");
        Document doc3 = new Document("文档3");

        RerankResult result1 = new RerankResult(doc1, 0.9);
        RerankResult result2 = new RerankResult(doc2, 0.5);
        RerankResult result3 = new RerankResult(doc3, 0.7);

        // 验证比较功能（降序）
        assertTrue(result1.compareTo(result2) < 0, "分数高的应该排在前面");
        assertTrue(result2.compareTo(result1) > 0, "分数低的应该排在后面");
        assertTrue(result1.compareTo(result3) < 0, "0.9 应该排在 0.7 前面");
    }

    @Test
    void testRerankResult_getter方法() {
        Document doc = new Document("测试文档内容");
        RerankResult result = new RerankResult(doc, 0.85);

        assertEquals(doc, result.getDocument(), "应该返回正确的文档");
        assertEquals(0.85, result.getRelevanceScore(), 0.001, "应该返回正确的分数");
    }

    @Test
    void testRerankResult_toString方法() {
        Document doc = new Document("这是一个测试文档内容，用于验证toString方法的输出格式");
        RerankResult result = new RerankResult(doc, 0.75);

        String str = result.toString();
        assertTrue(str.contains("0.75"), "toString应包含分数");
        assertTrue(str.contains("RerankResult"), "toString应包含类名");
    }

    @Test
    void testConstructor_参数正确设置() {
        // 验证构造函数不会抛出异常
        assertDoesNotThrow(() -> new RerankService(OLLAMA_BASE_URL, RERANK_MODEL));
    }
}
