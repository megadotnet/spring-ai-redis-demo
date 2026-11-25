package com.redis.demo.spring.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import com.redis.demo.spring.ai.memory.ConversationMemory;
import com.redis.demo.spring.ai.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * RagService native 写入端单元测试（方案 A）：
 * - 成功回答后写入 user + assistant 两条消息到共用的 ConversationMemory；
 * - 生成异常直接上抛，不写入不完整轮次；
 * - 记忆写入失败被吞掉，不影响已成功的回答；
 * - chatId 为空或回答为空时跳过写入。
 */
@ExtendWith(MockitoExtension.class)
class RagServiceNativeHistoryTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private ConversationMemory memory;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(chatModel, vectorStore, null, null, memory);
        ReflectionTestUtils.setField(ragService, "systemBeerPrompt",
            new ClassPathResource("prompts/system-qa.st"));
        ReflectionTestUtils.setField(ragService, "topK", 10);
    }

    private void stubSuccessfulGeneration(String answer) {
        stubGeneration();
        when(generation.getOutput()).thenReturn(new AssistantMessage(answer));
    }

    private void stubGeneration() {
        List<Document> docs = Arrays.asList(new Document("测试文档"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
    }

    @Test
    void 成功回答应写入user与assistant两条记忆() {
        stubSuccessfulGeneration("这是 native 回答");

        Generation result = ragService.retrieve("chat-1", "native 提问");

        assertSame(generation, result);
        verify(memory, times(1)).append("chat-1", "user", "native 提问");
        verify(memory, times(1)).append("chat-1", "assistant", "这是 native 回答");
    }

    @Test
    void 生成异常应上抛且不写入记忆() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(Arrays.asList(new Document("测试文档")));
        when(chatModel.call(any(Prompt.class)))
            .thenThrow(new RuntimeException("LLM 服务异常"));

        assertThrows(RuntimeException.class, () -> ragService.retrieve("chat-2", "问题"));
        verify(memory, never()).append(anyString(), anyString(), anyString());
    }

    @Test
    void 记忆写入失败应被吞掉且正常返回回答() {
        stubSuccessfulGeneration("回答内容");
        doThrow(new RuntimeException("Redis down"))
            .when(memory).append(anyString(), anyString(), anyString());

        Generation result = assertDoesNotThrow(
            () -> ragService.retrieve("chat-3", "问题"));

        assertSame(generation, result);
    }

    @Test
    void 空回答不应写入记忆() {
        stubSuccessfulGeneration("   ");

        ragService.retrieve("chat-4", "问题");

        verify(memory, never()).append(anyString(), anyString(), anyString());
    }

    @Test
    void chatId为空应跳过写入但仍返回回答() {
        // chatId 为空时 saveTurns 提前返回，不会触发 getOutput()，
        // 严格桩模式下不能多埋 getOutput 的 stub
        stubGeneration();

        assertSame(generation, ragService.retrieve("", "问题"));
        assertSame(generation, ragService.retrieve("   ", "问题"));
        verify(memory, never()).append(anyString(), anyString(), anyString());
    }
}
