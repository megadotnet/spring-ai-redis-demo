package com.redis.demo.spring.ai.memory;

import com.redis.demo.spring.ai.model.AgentTaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 实现的 Agent 任务状态存储。
 * 将有限大小的任务状态序列化为 JSON 存入 Redis，TTL 7 天。
 */
@Repository
public class RedisAgentTaskStore implements AgentTaskStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisAgentTaskStore.class);
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public RedisAgentTaskStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(AgentTaskState state) {
        try {
            String json = mapper.writeValueAsString(state);
            redis.opsForValue().set(key(state.traceId()), json, TTL);
        } catch (Exception ex) {
            // 任务状态保存失败不应阻塞用户请求
            logger.error("Could not save Agent task state for traceId={}: {}",
                    state.traceId(), ex.getMessage());
        }
    }

    @Override
    public Optional<AgentTaskState> find(String traceId) {
        String json = redis.opsForValue().get(key(traceId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, AgentTaskState.class));
        } catch (Exception ex) {
            logger.error("Could not read Agent task state for traceId={}: {}",
                    traceId, ex.getMessage());
            return Optional.empty();
        }
    }

    private String key(String traceId) {
        return "rag:agent:task:" + traceId;
    }
}
