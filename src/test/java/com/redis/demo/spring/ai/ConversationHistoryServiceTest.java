package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.memory.ConversationMemory;
import com.redis.demo.spring.ai.memory.RedisConversationMemory;
import com.redis.demo.spring.ai.model.ConversationHistoryResponse;
import com.redis.demo.spring.ai.service.ConversationHistoryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConversationHistoryService 单元测试（两条通道共用的历史读取服务）：
 * - 正常读取按时间正序的 role/content 消息；
 * - sessionId 为空时返回空历史而不是抛异常；
 * - 会话不存在（memory 返回空列表）时返回空消息；
 * - 脏数据（空内容/未知角色）被过滤或归一化；
 * - Redis 读取失败包装为 IllegalStateException。
 */
class ConversationHistoryServiceTest {

    private static ConversationMemory memoryReturning(List<ConversationMemory.Turn> turns) {
        return new ConversationMemory() {
            @Override
            public List<Turn> recent(String sessionId) {
                return turns;
            }

            @Override
            public void append(String sessionId, String role, String content) {
                // 历史读取不涉及写入
            }
        };
    }

    @Test
    void shouldReturnHistoryInChronologicalOrder() {
        ConversationHistoryService service = new ConversationHistoryService(memoryReturning(List.of(
                new ConversationMemory.Turn("user", "第一问"),
                new ConversationMemory.Turn("assistant", "第一答")
        )));

        ConversationHistoryResponse response = service.history("s1");

        assertEquals("s1", response.sessionId());
        assertEquals(2, response.messages().size());
        assertEquals("user", response.messages().get(0).role());
        assertEquals("第一问", response.messages().get(0).content());
        assertEquals("assistant", response.messages().get(1).role());
        assertEquals("第一答", response.messages().get(1).content());
    }

    @Test
    void shouldReturnEmptyHistoryForBlankSessionId() {
        ConversationHistoryService service = new ConversationHistoryService(memoryReturning(List.of()));

        assertTrue(service.history(null).messages().isEmpty());
        assertTrue(service.history("  ").messages().isEmpty());
    }

    @Test
    void shouldReturnEmptyMessagesWhenSessionMissing() {
        ConversationHistoryService service = new ConversationHistoryService(memoryReturning(List.of()));

        ConversationHistoryResponse response = service.history("unknown-session");

        assertEquals("unknown-session", response.sessionId());
        assertTrue(response.messages().isEmpty());
    }

    @Test
    void shouldFilterBlankContentAndNormalizeUnknownRole() {
        ConversationHistoryService service = new ConversationHistoryService(memoryReturning(List.of(
                new ConversationMemory.Turn("system", "脏数据角色"),
                new ConversationMemory.Turn("user", " "),
                new ConversationMemory.Turn(null, null)
        )));

        ConversationHistoryResponse response = service.history("s2");

        assertEquals(1, response.messages().size());
        assertEquals("assistant", response.messages().get(0).role());
        assertEquals("脏数据角色", response.messages().get(0).content());
    }

    @Test
    void shouldWrapMemoryFailureIntoIllegalState() {
        ConversationMemory broken = new ConversationMemory() {
            @Override
            public List<Turn> recent(String sessionId) {
                throw new IllegalStateException("Redis connection refused");
            }

            @Override
            public void append(String sessionId, String role, String content) {
            }
        };
        ConversationHistoryService service = new ConversationHistoryService(broken);

        assertThrows(IllegalStateException.class, () -> service.history("s3"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldTolerateNullRoleAndContentInRedisConversationMemoryAppend() {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        ListOperations<String, String> listOps = Mockito.mock(ListOperations.class);
        Mockito.when(redis.opsForList()).thenReturn(listOps);

        RedisConversationMemory memory = new RedisConversationMemory(redis);
        memory.append("s-null", null, null);

        Mockito.verify(listOps).rightPush(Mockito.eq("rag:session:s-null"), Mockito.anyString());
    }
}
