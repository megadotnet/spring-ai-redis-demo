package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.agent.AgentRuntimeService;
import com.redis.demo.spring.ai.model.AgentChatHistoryResponse;
import com.redis.demo.spring.ai.model.AgentChatRequest;
import com.redis.demo.spring.ai.model.AgentChatResponse;
import com.redis.demo.spring.ai.service.AgentHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 聊天 API：Agentic RAG MVP 的灰度入口。
 * 旧 /chat/{chatId}（RagController）保持不动，作为 legacy 回滚路径；
 * 前端可逐步从 chat/{chatId} 切换到 api/agent/chat。
 * Controller 不承担规划逻辑，只校验请求并委托 AgentRuntimeService。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    private final AgentRuntimeService agentRuntimeService;
    private final AgentHistoryService agentHistoryService;

    public AgentChatController(AgentRuntimeService agentRuntimeService,
            AgentHistoryService agentHistoryService) {
        this.agentRuntimeService = agentRuntimeService;
        this.agentHistoryService = agentHistoryService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        return ResponseEntity.ok(agentRuntimeService.run(request));
    }

    /**
     * 读取 Agentic 会话的对话历史，供前端首次进入页面时恢复消息列表。
     * Controller 只做参数校验与委托：历史读取、角色归一化都在 AgentHistoryService；
     * 会话不存在/无历史返回空列表（200），Redis 读取失败转 500 + 可读 message。
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<?> history(@PathVariable("sessionId") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "sessionId 不能为空"));
        }
        try {
            AgentChatHistoryResponse history = agentHistoryService.history(sessionId);
            return ResponseEntity.ok(history);
        } catch (IllegalStateException ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "会话历史读取失败，请稍后重试"));
        }
    }
}
