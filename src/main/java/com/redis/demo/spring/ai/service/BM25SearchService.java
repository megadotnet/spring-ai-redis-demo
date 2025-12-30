package com.redis.demo.spring.ai.service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * BM25 全文检索服务
 * 基于 Apache Lucene 实现的内存索引 BM25 检索
 * 支持通过 Redis 持久化文档数据，应用重启后自动恢复索引
 *
 * @author Spring AI RAG Demo
 * @since 2024-12
 */
public class BM25SearchService {

    private static final Logger logger = LoggerFactory.getLogger(BM25SearchService.class);

    private static final String FIELD_ID = "id";
    private static final String FIELD_CONTENT = "content";

    private Directory directory;
    private Analyzer analyzer;
    private final Map<String, Document> documentStore;
    private final ReadWriteLock lock;
    private boolean indexBuilt;

    // Redis 持久化服务（可选）
    private BM25DocumentPersistenceService persistenceService;

    public BM25SearchService() {
        this.directory = new ByteBuffersDirectory();
        this.analyzer = new StandardAnalyzer();
        this.documentStore = new HashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.indexBuilt = false;
    }

    /**
     * 设置 Redis 持久化服务
     *
     * @param persistenceService Redis 持久化服务
     */
    public void setPersistenceService(BM25DocumentPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * 从 Redis 恢复索引（应用启动时调用）
     *
     * @return 是否成功恢复
     */
    public boolean restoreFromPersistence() {
        if (persistenceService == null) {
            logger.debug("No persistence service configured, skipping restore");
            return false;
        }

        if (!persistenceService.hasPersistedDocuments()) {
            logger.info("No persisted documents found in Redis");
            return false;
        }

        try {
            List<Document> documents = persistenceService.loadDocuments();
            if (!documents.isEmpty()) {
                indexDocumentsInternal(documents, false); // 不保存到 Redis（已经从 Redis 加载）
                logger.info("Restored BM25 index from Redis with {} documents", documents.size());
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to restore BM25 index from Redis: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * 构建或更新 BM25 索引
     *
     * @param documents 文档列表
     */
    public void indexDocuments(List<Document> documents) {
        indexDocumentsInternal(documents, true);
    }

    /**
     * 内部索引方法
     *
     * @param documents      文档列表
     * @param persistToRedis 是否持久化到 Redis
     */
    private void indexDocumentsInternal(List<Document> documents, boolean persistToRedis) {
        if (documents == null || documents.isEmpty()) {
            logger.warn("No documents to index");
            return;
        }

        lock.writeLock().lock();
        try {
            // 重新创建索引目录
            directory = new ByteBuffersDirectory();
            documentStore.clear();

            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity(1.2f, 0.75f)); // BM25 参数 k1 和 b
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (Document doc : documents) {
                    String docId = doc.getId() != null ? doc.getId() : UUID.randomUUID().toString();
                    String content = doc.getText();

                    // 创建 Lucene 文档
                    org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();
                    luceneDoc.add(new StringField(FIELD_ID, docId, Field.Store.YES));
                    luceneDoc.add(new TextField(FIELD_CONTENT, content, Field.Store.NO));

                    writer.addDocument(luceneDoc);

                    // 保存原始文档以便后续返回
                    documentStore.put(docId, doc);
                }
                writer.commit();
            }

            indexBuilt = true;
            logger.info("BM25 index built successfully with {} documents", documents.size());

            // 持久化到 Redis（带 OOM 保护）
            if (persistToRedis && persistenceService != null) {
                boolean saved = persistenceService.saveDocuments(documents);
                if (saved) {
                    logger.info("Documents persisted to Redis Cloud");
                } else {
                    logger.warn("Redis persistence skipped (OOM or unavailable), using memory-only mode");
                }
            }

        } catch (IOException e) {
            logger.error("Failed to build BM25 index: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build BM25 index", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 追加文档到现有索引
     *
     * @param documents 新文档列表
     */
    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        lock.writeLock().lock();
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity(1.2f, 0.75f));
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (Document doc : documents) {
                    String docId = doc.getId() != null ? doc.getId() : UUID.randomUUID().toString();
                    String content = doc.getText();

                    org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();
                    luceneDoc.add(new StringField(FIELD_ID, docId, Field.Store.YES));
                    luceneDoc.add(new TextField(FIELD_CONTENT, content, Field.Store.NO));

                    writer.addDocument(luceneDoc);
                    documentStore.put(docId, doc);
                }
                writer.commit();
            }

            logger.info("Added {} documents to BM25 index", documents.size());

            // 追加持久化到 Redis
            if (persistenceService != null) {
                persistenceService.appendDocuments(documents);
            }

        } catch (IOException e) {
            logger.error("Failed to add documents to BM25 index: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 执行 BM25 搜索
     *
     * @param queryText 查询文本
     * @param topK      返回的文档数量
     * @return 匹配的文档列表（按 BM25 分数降序）
     */
    public List<ScoredDocument> search(String queryText, int topK) {
        if (!indexBuilt || queryText == null || queryText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        lock.readLock().lock();
        try {
            DirectoryReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity(1.2f, 0.75f));

            // 转义特殊字符并创建查询
            String escapedQuery = QueryParser.escape(queryText.trim());
            if (escapedQuery.isEmpty()) {
                return Collections.emptyList();
            }

            QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
            Query query = parser.parse(escapedQuery);

            TopDocs topDocs = searcher.search(query, topK);
            List<ScoredDocument> results = new ArrayList<>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                org.apache.lucene.document.Document luceneDoc = searcher.doc(scoreDoc.doc);
                String docId = luceneDoc.get(FIELD_ID);

                Document originalDoc = documentStore.get(docId);
                if (originalDoc != null) {
                    results.add(new ScoredDocument(originalDoc, scoreDoc.score, results.size() + 1));
                }
            }

            reader.close();

            logger.debug("BM25 search for '{}' returned {} results",
                    queryText.substring(0, Math.min(30, queryText.length())), results.size());

            return results;

        } catch (Exception e) {
            logger.error("BM25 search failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 清空索引（同时清除 Redis 持久化数据）
     */
    public void clearIndex() {
        lock.writeLock().lock();
        try {
            directory = new ByteBuffersDirectory();
            documentStore.clear();
            indexBuilt = false;

            // 清除 Redis 持久化数据
            if (persistenceService != null) {
                persistenceService.clearDocuments();
            }

            logger.info("BM25 index cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取索引中的文档数量
     */
    public int getDocumentCount() {
        return documentStore.size();
    }

    /**
     * 检查索引是否已构建
     */
    public boolean isIndexBuilt() {
        return indexBuilt;
    }

    /**
     * 带分数的文档结果类
     */
    public static class ScoredDocument {
        private final Document document;
        private final float score;
        private final int rank;

        public ScoredDocument(Document document, float score, int rank) {
            this.document = document;
            this.score = score;
            this.rank = rank;
        }

        public Document getDocument() {
            return document;
        }

        public float getScore() {
            return score;
        }

        public int getRank() {
            return rank;
        }
    }
}
