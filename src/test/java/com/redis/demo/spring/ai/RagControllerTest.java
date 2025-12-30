package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.model.RetrievalResult;
import com.redis.demo.spring.ai.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagController.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagService ragService;

    @Test
    void retrieveShouldReturnRetrievalResults() throws Exception {
        // 准备测试数据
        Map<String, String> payload = Map.of("query", "test query", "topK", "5");
        List<RetrievalResult> mockResults = List.of(
            new RetrievalResult("id1", "content1", 0.8, Map.of("filename", "file1.pdf")),
            new RetrievalResult("id2", "content2", 0.7, Map.of("filename", "file2.pdf"))
        );

        // 模拟服务调用
        when(ragService.retrieveForTesting(any(Map.class))).thenReturn(mockResults);

        // 执行请求并验证响应
        mockMvc.perform(post("/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"test query\",\"topK\":\"5\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("id1"))
                .andExpect(jsonPath("$[0].content").value("content1"))
                .andExpect(jsonPath("$[0].score").value(0.8))
                .andExpect(jsonPath("$[1].id").value("id2"))
                .andExpect(jsonPath("$[1].content").value("content2"))
                .andExpect(jsonPath("$[1].score").value(0.7));
    }

    @Test
    void retrieveShouldHandleMinimalPayload() throws Exception {
        // 准备测试数据 - 最小有效载荷（仅包含查询）
        Map<String, String> payload = Map.of("query", "minimal query");
        List<RetrievalResult> mockResults = List.of(
            new RetrievalResult("id1", "content1", 0.8, Map.of("filename", "file1.pdf"))
        );

        // 模拟服务调用
        when(ragService.retrieveForTesting(any(Map.class))).thenReturn(mockResults);

        // 执行请求并验证响应
        mockMvc.perform(post("/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"minimal query\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("id1"))
                .andExpect(jsonPath("$[0].content").value("content1"))
                .andExpect(jsonPath("$[0].score").value(0.8));
    }

    @Test
    void retrieveShouldHandleEmptyPayload() throws Exception {
        // 准备测试数据 - 空载荷
        List<RetrievalResult> mockResults = List.of();

        // 模拟服务调用
        when(ragService.retrieveForTesting(any(Map.class))).thenReturn(mockResults);

        // 执行请求并验证响应
        mockMvc.perform(post("/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}