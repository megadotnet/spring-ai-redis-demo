package com.redis.demo.spring.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import com.redis.demo.spring.ai.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * RagService 单元测试类
 * 测试检索增强生成服务的核心功能
 */
@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(chatModel, vectorStore);
        // 设置私有字段的值
        ReflectionTestUtils.setField(ragService, "systemBeerPrompt", 
            new ClassPathResource("prompts/system-qa.st"));
        ReflectionTestUtils.setField(ragService, "topK", 10);
    }

    @Test
    void testRetrieve_成功检索并生成回答() {
        // 准备测试数据
        String userMessage = "什么是Spring AI？";
        String expectedAnswer = "Spring AI是一个用于构建AI应用的框架";
        
        // 模拟文档数据
        Document doc1 = new Document("Spring AI是一个强大的AI框架");
        Document doc2 = new Document("它提供了与各种AI模型的集成能力");
        List<Document> mockDocuments = Arrays.asList(doc1, doc2);
        
        // 配置mock行为
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(mockDocuments);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        
        // 执行测试
        Generation result = ragService.retrieve(userMessage);
        
        // 验证结果
        assertNotNull(result, "返回结果不应为空");
        assertEquals(generation, result, "应该返回预期的Generation对象");
        
        // 验证交互
        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void testRetrieve_验证搜索请求参数() {
        // 准备测试数据
        String userMessage = "测试查询";
        List<Document> mockDocuments = Arrays.asList(new Document("测试文档"));
        
        // 配置mock行为，只保留测试关注的部分
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(mockDocuments);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        
        // 执行测试
        ragService.retrieve(userMessage);
        
        // 验证搜索请求参数
        verify(vectorStore).similaritySearch(argThat((SearchRequest request) -> {
            assertEquals(userMessage, request.getQuery(), "查询内容应该匹配");
            assertEquals(10, request.getTopK(), "TopK值应该为10");
            return true;
        }));
    }

    @Test
    void testRetrieve_空文档列表处理() {
        // 准备测试数据
        String userMessage = "测试查询";
        List<Document> emptyDocuments = Arrays.asList();
        
        // 配置mock行为，只保留测试关注的部分
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(emptyDocuments);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        
        // 执行测试
        Generation result = ragService.retrieve(userMessage);
        
        // 验证结果
        assertNotNull(result, "即使没有相关文档，也应该返回结果");
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    
    void testRetrieve_多个文档内容合并() {
        // 准备测试数据
        String userMessage = "测试查询";
        Document doc1 = new Document("第一个文档内容");
        Document doc2 = new Document("第二个文档内容");
        Document doc3 = new Document("第三个文档内容");
        List<Document> mockDocuments = Arrays.asList(doc1, doc2, doc3);
        
        // 配置mock行为
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(mockDocuments);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("综合回答"));
        
        // 执行测试
        ragService.retrieve(userMessage);
        
        // 验证Prompt包含了所有文档内容
        verify(chatModel).call(argThat((Prompt prompt) -> {
            String promptString = prompt.toString();
            // 验证所有文档内容都被包含在prompt中
            assertTrue(promptString.contains("第一个文档内容") || 
                      prompt.getInstructions().stream()
                          .anyMatch(msg -> msg.toString().contains("第一个文档内容")),
                      "Prompt应该包含第一个文档内容");
            return true;
        }));
    }

    @Test
    void testRetrieve_异常处理_VectorStore异常() {
        // 准备测试数据
        String userMessage = "测试查询";
        
        // 配置mock抛出异常
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenThrow(new RuntimeException("向量存储异常"));
        
        // 执行测试并验证异常
        assertThrows(RuntimeException.class, () -> {
            ragService.retrieve(userMessage);
        }, "应该抛出向量存储异常");
        
        // 验证ChatModel没有被调用
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void testRetrieve_异常处理_ChatModel异常() {
        // 准备测试数据
        String userMessage = "测试查询";
        List<Document> mockDocuments = Arrays.asList(new Document("测试文档"));
        
        // 配置mock行为
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(mockDocuments);
        when(chatModel.call(any(Prompt.class)))
            .thenThrow(new RuntimeException("聊天模型异常"));
        
        // 执行测试并验证异常
        assertThrows(RuntimeException.class, () -> {
            ragService.retrieve(userMessage);
        }, "应该抛出聊天模型异常");
        
        // 验证VectorStore被正常调用
        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void testConstructor_正常初始化() {
        // 测试构造函数
        RagService newService = new RagService(chatModel, vectorStore);
        
        assertNotNull(newService, "RagService应该成功创建");
    }

    @Test
    void testConstructor_空参数处理() {
        // 测试空参数的构造函数
        assertThrows(IllegalArgumentException.class, () -> {
            new RagService(null, vectorStore).retrieve("测试");
        }, "ChatModel为空时应该抛出异常");
        
        assertThrows(NullPointerException.class, () -> {
            new RagService(chatModel, null).retrieve("测试");
        }, "VectorStore为空时应该抛出异常");
    }
    
    void testRetrieve_自定义TopK值() {
        // 设置自定义topK值
        ReflectionTestUtils.setField(ragService, "topK", 5);
        
        String userMessage = "测试查询";
        List<Document> mockDocuments = Arrays.asList(new Document("测试文档"));
        
        // 配置mock行为，只保留测试关注的部分
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(mockDocuments);
        
        // 执行测试
        ragService.retrieve(userMessage);
        
        // 验证使用了自定义的topK值
        verify(vectorStore).similaritySearch(argThat((SearchRequest request) -> {
            assertEquals(5, request.getTopK(), "应该使用自定义的TopK值5");
            return true;
        }));
    }
}