package com.redis.demo.spring.ai;

import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.proto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PineconeDocumentChecker implements DocumentCountProvider {

    private final Pinecone pineconeClient;
    private final String indexName;

    public PineconeDocumentChecker(Pinecone pineconeClient,
                                   @Value("${spring.ai.vectorstore.pinecone.index-name}") String indexName) {
        this.pineconeClient = pineconeClient;
        this.indexName = indexName;
    }

    /**
     * 获取 Pinecone 索引中的向量总数。
     * @return 索引中的向量总数；如果查询失败或索引不存在，则返回 -1。
     */
    @Override
    public int getDocumentCount(String indexName) {
        try {
            Index index = pineconeClient.getIndexConnection(indexName);
            DescribeIndexStatsResponse stats = index.describeIndexStats();

            return stats.getTotalVectorCount();
        } catch (Exception e) {
            System.err.println("获取文档数量时出错: " + e.getMessage());
            return 0;
        }
    }

}

