package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.memory.AgentTaskStore;
import com.redis.demo.spring.ai.model.AgentTaskState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 任务状态查询 API。
 * 用于后台诊断和评估，不向客户端暴露内部异常堆栈或完整 prompt。
 *
 * 注意：生产环境应加认证与授权，调用者只能读取自己有权限的 session/task。
 */
@RestController
@RequestMapping("/api/agent/tasks")
public class AgentTaskController {

    private final AgentTaskStore taskStore;

    public AgentTaskController(AgentTaskStore taskStore) {
        this.taskStore = taskStore;
    }

    @GetMapping("/{traceId}")
    public ResponseEntity<AgentTaskState> getTask(
            @PathVariable String traceId) {
        return taskStore.find(traceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
