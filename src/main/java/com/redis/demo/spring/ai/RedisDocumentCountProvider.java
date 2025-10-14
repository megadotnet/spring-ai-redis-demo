package com.redis.demo.spring.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Redis VectorStore的文档数量提供者实现
 */
@Component
public class RedisDocumentCountProvider implements DocumentCountProvider {
    
    private final VectorStore vectorStore;
    
    public RedisDocumentCountProvider(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    
    @Override
    public int getDocumentCount(String indexName) {
        if (!(vectorStore instanceof RedisVectorStore)) {
            List<Document> documentList= vectorStore.similaritySearch(SearchRequest.builder().query("").topK(1).build());
            if (documentList!=null)
            {
                return documentList.size();
            }
            else
            {
                throw new UnsupportedOperationException("当前VectorStore实现不支持文档数量查询,vectorStore查询Document异常");
            }


        }
        
        RedisVectorStore redisVectorStore = (RedisVectorStore) vectorStore;

        Map<String, Object> indexInfo = redisVectorStore.getJedis().ftInfo(indexName);
        return Integer.parseInt(String.valueOf(indexInfo.getOrDefault("num_docs", "0")));
    }
}