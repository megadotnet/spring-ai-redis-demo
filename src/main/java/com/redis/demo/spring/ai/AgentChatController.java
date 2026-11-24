package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.agent.AgentRuntimeService;
import com.redis.demo.spring.ai.model.AgentChatRequest;
import com.redis.demo.spring.ai.model.AgentChatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public AgentChatController(AgentRuntimeService agentRuntimeService) {
        this.agentRuntimeService = agentRuntimeService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        return ResponseEntity.ok(agentRuntimeService.run(request));
    }
}
