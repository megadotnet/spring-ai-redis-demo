package com.redis.demo.spring.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

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
}
