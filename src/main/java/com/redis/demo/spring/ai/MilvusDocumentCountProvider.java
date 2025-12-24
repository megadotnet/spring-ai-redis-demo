package com.redis.demo.spring.ai;

import io.milvus.grpc.FlushResponse;
import io.milvus.grpc.GetCollectionStatisticsResponse;
import io.milvus.grpc.KeyValuePair;
import io.milvus.param.collection.FlushParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.collection.GetCollectionStatisticsParam;

/**
 * Milvus专用文档数量提供者
 */
@Component
public class MilvusDocumentCountProvider implements DocumentCountProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(MilvusDocumentCountProvider.class);
    
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
        
        logger.debug("获取文档数量，目标集合: {}", targetCollectionName);

        FlushParam flushParam = FlushParam.newBuilder()
                .addCollectionName(targetCollectionName)
                .build();

        logger.debug("执行 Flush 操作，集合: {}", targetCollectionName);
        R<FlushResponse> flushResponse = milvusClient.flush(flushParam);

        if (flushResponse.getStatus() != R.Status.Success.getCode()) {
            logger.error("Flush 失败，集合: {}, 错误信息: {}", targetCollectionName, flushResponse.getMessage());
            throw new RuntimeException("Flush failed: " + flushResponse.getMessage());
        } else {
            logger.info("Flush 成功，集合: {}，正在等待数据落盘...", targetCollectionName);
        }

        // Flush 是异步的，建议稍作等待或轮询状态
        try {
            logger.debug("等待2秒以确保数据落盘");
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            logger.warn("线程在等待期间被中断: {}", e.getMessage());
            Thread.currentThread().interrupt(); // 重新设置中断状态
        }

        // 使用Milvus客户端获取集合统计信息
        try {
            logger.debug("获取集合统计信息，集合: {}", targetCollectionName);
            R<GetCollectionStatisticsResponse> response = milvusClient.getCollectionStatistics(
                GetCollectionStatisticsParam.newBuilder()
                    .withCollectionName(targetCollectionName)
                    .build()
            );
            
            if (response.getStatus() == 0) { // 0表示成功
                logger.debug("成功获取集合统计信息，集合: {}", targetCollectionName);
                // 解析 row_count
                List<KeyValuePair> stats = response.getData().getStatsList();
                for (KeyValuePair kv : stats) {
                    if ("row_count".equals(kv.getKey())) {
                        int count = Integer.parseInt(kv.getValue());
                        logger.info("集合 {} 的文档数量: {}", targetCollectionName, count);
                        return count;
                    }
                }
                logger.warn("在集合统计信息中未找到 row_count，集合: {}", targetCollectionName);
                return 0;
            } else {
                logger.error("获取集合统计信息失败，集合: {}, 错误信息: {}", targetCollectionName, response.getMessage());
                throw new RuntimeException("Failed to get collection statistics: " + response.getMessage());
            }
        } catch (Exception e) {
            logger.error("无法获取Milvus集合统计信息，集合: {}", targetCollectionName, e);
            throw new UnsupportedOperationException(
                "无法获取Milvus集合统计信息: " + e.getMessage(), e);
        }
    }
}