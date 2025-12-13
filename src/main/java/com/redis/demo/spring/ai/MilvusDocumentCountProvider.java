package com.redis.demo.spring.ai;

import io.milvus.grpc.GetCollectionStatisticsResponse;
import io.milvus.grpc.KeyValuePair;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.response.GetCollStatResponseWrapper;

/**
 * Milvus专用文档数量提供者
 */
@Component
public class MilvusDocumentCountProvider implements DocumentCountProvider {
    
    private final VectorStore vectorStore;
    private final MilvusServiceClient milvusClient;
    private final String collectionName;
    
    public MilvusDocumentCountProvider(VectorStore vectorStore, 
                                     MilvusServiceClient milvusClient,
                                     @Value("${spring.ai.vectorstore.milvus.collection-name:#{null}}") String collectionName) {
        this.vectorStore = vectorStore;
        this.milvusClient = milvusClient;
        this.collectionName = collectionName;
    }
    
    @Override
    public int getDocumentCount(String indexName) {
        // 如果传入的indexName为空，使用配置的collectionName
        String targetCollectionName = (indexName != null && !indexName.isEmpty()) ? indexName : collectionName;
        
        // 使用Milvus客户端获取集合统计信息
        try {
            R<GetCollectionStatisticsResponse> response = milvusClient.getCollectionStatistics(
                GetCollectionStatisticsParam.newBuilder()
                    .withCollectionName(targetCollectionName)
                    .build()
            );
            
            // 返回实体数量
            if (response.getStatus() == 0) { // 0表示成功
                // 5. 解析 row_count
                List<KeyValuePair> stats = response.getData().getStatsList();
                for (KeyValuePair kv : stats) {
                    if ("row_count".equals(kv.getKey())) {
                        return Integer.parseInt(kv.getValue());
                    }
                }
                return 0;
            } else {
                throw new RuntimeException("Failed to get collection statistics: " + response.getMessage());
            }
        } catch (Exception e) {
            throw new UnsupportedOperationException(
                "无法获取Milvus集合统计信息: " + e.getMessage(), e);
        }
    }
}