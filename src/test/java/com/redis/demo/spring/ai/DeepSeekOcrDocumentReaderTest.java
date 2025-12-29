package com.redis.demo.spring.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.redis.demo.spring.ai.service.DeepSeekOcrDocumentReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

/**
 * DeepSeekOcrDocumentReader 单元测试类
 * 测试 DeepSeek-OCR PDF 文档读取器的核心功能
 */
@ExtendWith(MockitoExtension.class)
class DeepSeekOcrDocumentReaderTest {

    private DeepSeekOcrDocumentReader ocrReader;

    private static final String TEST_API_KEY = "test-api-key";
    private static final String OCR_MODEL = "deepseek-ai/DeepSeek-OCR";

    @BeforeEach
    void setUp() {
        ocrReader = new DeepSeekOcrDocumentReader(TEST_API_KEY, OCR_MODEL);
    }

    @Test
    void testConstructor_使用API_Key和模型() {
        // 验证构造函数不会抛出异常
        assertDoesNotThrow(() -> new DeepSeekOcrDocumentReader(TEST_API_KEY));
        assertDoesNotThrow(() -> new DeepSeekOcrDocumentReader(TEST_API_KEY, OCR_MODEL));
    }

    @Test
    void testConstructor_空API_Key不抛异常() {
        // 空 API Key 应该记录警告但不抛出异常
        assertDoesNotThrow(() -> new DeepSeekOcrDocumentReader(null));
        assertDoesNotThrow(() -> new DeepSeekOcrDocumentReader(""));
        assertDoesNotThrow(() -> new DeepSeekOcrDocumentReader("  "));
    }

    @Test
    void testRead_null资源返回空列表() throws Exception {
        // null 资源应该返回空列表
        List<Document> result = ocrReader.read(null);
        assertNotNull(result, "结果不应为空");
        assertTrue(result.isEmpty(), "null 资源应返回空列表");
    }

    @Test
    void testConstructor_使用默认模型() {
        // 测试只传入 API Key 时使用默认模型
        DeepSeekOcrDocumentReader reader = new DeepSeekOcrDocumentReader(TEST_API_KEY);
        assertNotNull(reader, "Reader 应该被成功创建");
    }

    /**
     * 测试并行任务拼接文档的顺序
     * 模拟多个页面并行处理，验证最终结果按页码顺序拼接
     */
    @Test
    void testParallelProcessing_文档拼接顺序正确() throws Exception {
        // 模拟页面数据：5个页面
        List<String> pageContents = Arrays.asList(
                "Page1Content", "Page2Content", "Page3Content",
                "Page4Content", "Page5Content");

        // 创建线程池模拟并行处理
        ExecutorService executorService = Executors.newFixedThreadPool(4);

        // 模拟并行任务，添加随机延迟使任务完成顺序不确定
        List<CompletableFuture<PageResultForTest>> futures = new ArrayList<>();
        for (int i = 0; i < pageContents.size(); i++) {
            final int pageIndex = i;
            final String content = pageContents.get(i);

            CompletableFuture<PageResultForTest> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // 模拟不同页面处理时间不同，导致完成顺序不确定
                    // 页面0延迟最长，页面4延迟最短
                    Thread.sleep((5 - pageIndex) * 50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new PageResultForTest(pageIndex, content);
            }, executorService);

            futures.add(future);
        }

        // 等待所有任务完成并按页码顺序排序
        List<PageResultForTest> results = futures.stream()
                .map(CompletableFuture::join)
                .sorted((a, b) -> Integer.compare(a.pageIndex, b.pageIndex))
                .collect(Collectors.toList());

        // 合并所有页面内容
        StringBuilder allContent = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            allContent.append(results.get(i).content);
            if (i < results.size() - 1) {
                allContent.append("\n\n---\n\n");
            }
        }

        String mergedContent = allContent.toString();
        executorService.shutdown();

        // 验证拼接顺序正确
        assertEquals(5, results.size(), "应该有5个页面结果");

        // 验证页码顺序
        for (int i = 0; i < results.size(); i++) {
            assertEquals(i, results.get(i).pageIndex,
                    "第" + i + "个结果的页码应该是" + i);
        }

        // 验证内容顺序：Page1 应该在 Page2 之前
        assertTrue(mergedContent.indexOf("Page1Content") < mergedContent.indexOf("Page2Content"),
                "Page1 应该在 Page2 之前");
        assertTrue(mergedContent.indexOf("Page2Content") < mergedContent.indexOf("Page3Content"),
                "Page2 应该在 Page3 之前");
        assertTrue(mergedContent.indexOf("Page3Content") < mergedContent.indexOf("Page4Content"),
                "Page3 应该在 Page4 之前");
        assertTrue(mergedContent.indexOf("Page4Content") < mergedContent.indexOf("Page5Content"),
                "Page4 应该在 Page5 之前");

        // 验证分隔符存在
        assertTrue(mergedContent.contains("\n\n---\n\n"), "应该包含页面分隔符");
    }

    /**
     * 测试乱序完成的任务仍然按正确顺序拼接
     */
    @Test
    void testParallelProcessing_乱序完成仍按顺序拼接() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        // 创建任务，故意让后面的页面先完成
        List<CompletableFuture<PageResultForTest>> futures = new ArrayList<>();

        // 页面3先完成（无延迟）
        futures.add(CompletableFuture.supplyAsync(() -> {
            return new PageResultForTest(2, "ContentC");
        }, executorService));

        // 页面1后完成（延迟100ms）
        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
            return new PageResultForTest(0, "ContentA");
        }, executorService));

        // 页面2最后完成（延迟50ms）
        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
            return new PageResultForTest(1, "ContentB");
        }, executorService));

        // 等待并排序
        List<PageResultForTest> results = futures.stream()
                .map(CompletableFuture::join)
                .sorted((a, b) -> Integer.compare(a.pageIndex, b.pageIndex))
                .collect(Collectors.toList());

        // 合并内容
        StringBuilder allContent = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            allContent.append(results.get(i).content);
            if (i < results.size() - 1) {
                allContent.append(" | ");
            }
        }

        executorService.shutdown();

        // 验证排序后顺序正确
        assertEquals(0, results.get(0).pageIndex, "第一个应该是页面0");
        assertEquals(1, results.get(1).pageIndex, "第二个应该是页面1");
        assertEquals(2, results.get(2).pageIndex, "第三个应该是页面2");

        // 验证内容拼接顺序
        String content = allContent.toString();
        assertEquals("ContentA | ContentB | ContentC", content,
                "内容应该按 A, B, C 顺序拼接，而不是任务完成顺序");
    }

    /**
     * 测试单页面处理
     */
    @Test
    void testParallelProcessing_单页面处理() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(1);

        CompletableFuture<PageResultForTest> future = CompletableFuture.supplyAsync(() -> {
            return new PageResultForTest(0, "SinglePageContent");
        }, executorService);

        List<PageResultForTest> results = List.of(future.join());

        executorService.shutdown();

        assertEquals(1, results.size(), "应该只有1个页面结果");
        assertEquals(0, results.get(0).pageIndex, "页码应该是0");
        assertEquals("SinglePageContent", results.get(0).content, "内容应该正确");
    }

    /**
     * 测试页面结果辅助类（用于测试）
     */
    private static class PageResultForTest {
        final int pageIndex;
        final String content;

        PageResultForTest(int pageIndex, String content) {
            this.pageIndex = pageIndex;
            this.content = content;
        }
    }
}
