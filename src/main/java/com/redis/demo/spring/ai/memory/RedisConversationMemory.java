package com.redis.demo.spring.ai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis List 的短期会话记忆实现。
 * 保留有限轮数并设置 TTL；沿用项目的 Jackson 3（tools.jackson）API。
 *
 * 安全提示：当前 key 仅含 sessionId，生产使用前必须绑定租户/用户身份
 * （如 rag:session:{tenantId}:{userId}:{sessionId}）并校验调用者权限，
 * 本实现不代表安全隔离已经完成。
 */
@Repository
public class RedisConversationMemory implements ConversationMemory {

    private static final Logger logger = LoggerFactory.getLogger(RedisConversationMemory.class);

    private static final int MAX_MESSAGES = 12;
    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "rag:session:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public RedisConversationMemory(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public List<Turn> recent(String sessionId) {
        String key = key(sessionId);
        List<String> values = redis.opsForList().range(key, 0, -1);

        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<Turn> result = new ArrayList<>();
        for (String value : values) {
            try {
                Map<?, ?> map = objectMapper.readValue(value, Map.class);
                result.add(new Turn(
                        String.valueOf(map.get("role")),
                        String.valueOf(map.get("content"))
                ));
            } catch (Exception ignored) {
                // 忽略无法解析的历史记录，并由日志/指标记录异常。
                logger.warn("Skipped unparsable conversation record in session {}", sessionId);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void append(String sessionId, String role, String content) {
        String key = key(sessionId);
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "role", role,
                    "content", content == null ? "" : content
            ));
            redis.opsForList().rightPush(key, json);
            redis.opsForList().trim(key, -MAX_MESSAGES, -1);
            redis.expire(key, TTL);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist conversation memory", ex);
        }
    }

    private String key(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        return KEY_PREFIX + sessionId;
    }
}
