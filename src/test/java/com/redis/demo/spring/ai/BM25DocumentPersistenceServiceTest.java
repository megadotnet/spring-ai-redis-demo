package com.redis.demo.spring.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.redis.demo.spring.ai.service.BM25DocumentPersistenceService;
import com.redis.demo.spring.ai.service.BM25SearchService;

/**
 * BM25DocumentPersistenceService 单元测试。
 *
 * <p>用内存 fake 替代 Redis，聚焦重启恢复路径上的保真度：恢复出的文档必须带着写入时的 id
 * 与摄取期 metadata，否则 BM25 索引会与向量库中的同一 chunk 失去对应关系。
 * 不依赖 Docker，可确定性复现。
 */
@DisplayName("BM25 文档持久化：id 与 metadata 保真")
class BM25DocumentPersistenceServiceTest {

    private static final String KEY_PREFIX = "bm25:doc:";
    private static final String INDEX_KEY = "bm25:index:docs";

    private final Map<String, String> store = new HashMap<>();
    private final Set<String> index = new LinkedHashSet<>();

    private BM25DocumentPersistenceService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store.clear();
        index.clear();

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        when(valueOps.get(anyString()))
                .thenAnswer(invocation -> store.get(invocation.getArgument(0, String.class)));
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString());

        doAnswer(invocation -> {
            index.add(invocation.getArgument(1, String.class));
            return 1L;
        }).when(setOps).add(anyString(), any());

        when(setOps.members(anyString())).thenAnswer(invocation -> new LinkedHashSet<>(index));
        when(setOps.size(anyString())).thenAnswer(invocation -> (long) index.size());
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            if (INDEX_KEY.equals(key)) {
                index.clear();
                return true;
            }
            return store.remove(key) != null;
        });

        service = new BM25DocumentPersistenceService(redisTemplate);
    }

    @Test
    @DisplayName("保存再恢复：id 与摄取期 metadata 完整保留")
    void saveThenLoadKeepsIdAndIngestMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "beers.pdf");
        metadata.put("page", 7);
        metadata.put("chunk_type", "text");
        metadata.put("start_line", 120);
        Document doc = Document.builder()
                .id("chunk-42")
                .text("Milvus 是开源向量数据库")
                .metadata(metadata)
                .build();

        assertTrue(service.saveDocuments(List.of(doc)));

        List<Document> restored = service.loadDocuments();

        assertEquals(1, restored.size());
        Document loaded = restored.get(0);
        assertEquals("chunk-42", loaded.getId(), "恢复出的 id 必须与写入时的 id 一致，才能与向量库对齐");
        assertEquals("Milvus 是开源向量数据库", loaded.getText());
        assertEquals("beers.pdf", loaded.getMetadata().get("source"));
        assertEquals(7, loaded.getMetadata().get("page"));
        assertEquals("text", loaded.getMetadata().get("chunk_type"));
        assertEquals(120, loaded.getMetadata().get("start_line"));
    }

    @Test
    @DisplayName("检索期的临时分数字段不落盘")
    void saveDropsRetrievalTimeMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "beers.pdf");
        metadata.put("retrievalSource", "bm25");
        metadata.put("fusion_score", 0.0163);
        metadata.put("rerank_score", 0.93);
        metadata.put("distance", 0.21);
        service.saveDocuments(List.of(Document.builder()
                .id("chunk-7")
                .text("正文内容")
                .metadata(metadata)
                .build()));

        Map<String, Object> loaded = service.loadDocuments().get(0).getMetadata();

        assertEquals("beers.pdf", loaded.get("source"));
        assertFalse(loaded.containsKey("retrievalSource"), "陈旧检索来源不应落盘，否则会伪装成本次检索结果");
        assertFalse(loaded.containsKey("fusion_score"), "陈旧融合分数不应落盘");
        assertFalse(loaded.containsKey("rerank_score"), "陈旧重排分数不应落盘");
        assertFalse(loaded.containsKey("distance"), "陈旧距离不应落盘");
    }

    @Test
    @DisplayName("旧版数据缺少 metadata 字段时仍可恢复出正确 id")
    void loadToleratesLegacyPayloadWithoutMetadata() {
        index.add("legacy-1");
        store.put(KEY_PREFIX + "legacy-1", "{\"id\":\"legacy-1\",\"text\":\"旧数据正文\"}");

        List<Document> restored = service.loadDocuments();

        assertEquals(1, restored.size());
        assertEquals("legacy-1", restored.get(0).getId());
        assertEquals("旧数据正文", restored.get(0).getText());
        assertTrue(restored.get(0).getMetadata().isEmpty());
    }

    @Test
    @DisplayName("单条脏数据不拖垮整批恢复")
    void loadSkipsBrokenEntriesWithoutFailingTheBatch() {
        index.add("good");
        store.put(KEY_PREFIX + "good",
                "{\"id\":\"good\",\"text\":\"正常正文\",\"metadata\":{\"source\":\"a.pdf\"}}");
        index.add("broken");
        store.put(KEY_PREFIX + "broken", "这不是合法 JSON");
        index.add("orphan"); // 索引集合里还有条目，但 payload 已被删除

        List<Document> restored = service.loadDocuments();

        assertEquals(1, restored.size(), "只应恢复出完好的一条");
        assertEquals("good", restored.get(0).getId());
        assertEquals("a.pdf", restored.get(0).getMetadata().get("source"));
    }

    @Test
    @DisplayName("自动生成的 id 同样被保留为 Redis 键")
    void saveUsesGeneratedIdAsKey() {
        Document doc = new Document("无显式 id 的文档");

        service.saveDocuments(List.of(doc));

        Document restored = service.loadDocuments().get(0);
        assertTrue(store.containsKey(KEY_PREFIX + doc.getId()), "应沿用文档自带的 id 作为键");
        assertEquals(doc.getId(), restored.getId());
    }

    @Test
    @DisplayName("Redis 已有数据时不重复写入")
    void saveSkipsWhenDataAlreadyPresent() {
        service.saveDocuments(List.of(Document.builder().id("a").text("第一条").build()));
        service.saveDocuments(List.of(Document.builder().id("b").text("第二条").build()));

        assertEquals(1, service.getDocumentCount(), "第二次保存应被跳过");
        assertEquals("a", service.loadDocuments().get(0).getId());
    }

    @Test
    @DisplayName("超长文本按既有策略截断")
    void saveTruncatesLongText() {
        String longText = "长".repeat(2500);

        service.saveDocuments(List.of(Document.builder().id("big").text(longText).build()));

        Document restored = service.loadDocuments().get(0);
        assertEquals(2003, restored.getText().length(), "2000 字符正文加结尾省略号");
    }

    @Test
    @DisplayName("重启后从 Redis 恢复的索引，检索结果仍带向量库的原始 id")
    void restoredIndexKeepsOriginalIdsThroughSearch() {
        // 摄取：写入 Redis 的同时建立 BM25 索引
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "beers.pdf");
        Document ingested = Document.builder()
                .id("milvus-chunk-1")
                .text("Milvus 是一个开源的向量数据库")
                .metadata(metadata)
                .build();
        BM25SearchService bm25 = new BM25SearchService();
        bm25.setPersistenceService(service);
        bm25.indexDocuments(List.of(ingested));

        // 模拟重启：换一个新的 BM25SearchService（内存索引为空），只有 Redis 里的数据存活
        // 不能用 clearIndex()，它会把 Redis 持久化数据一并清掉
        BM25SearchService restarted = new BM25SearchService();
        restarted.setPersistenceService(service);
        assertTrue(restarted.restoreFromPersistence(), "Redis 有持久化数据时应能恢复索引");

        List<BM25SearchService.ScoredDocument> results = restarted.search("Milvus 向量数据库", 3);

        assertEquals(1, results.size());
        BM25SearchService.ScoredDocument hit = results.get(0);
        assertEquals("milvus-chunk-1", hit.getDocument().getId(),
                "恢复后的文档 id 必须仍是向量库里的 id，否则混合检索会把同一个 chunk 当成两个文档");
        assertEquals("beers.pdf", hit.getDocument().getMetadata().get("source"),
                "引用溯源依赖 source 字段，不能在恢复时丢失");
    }
}
