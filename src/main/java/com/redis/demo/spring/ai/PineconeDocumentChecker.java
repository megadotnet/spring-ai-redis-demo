package com.redis.demo.spring.ai;

import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.proto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PineconeDocumentChecker 类用于检查 Pinecone 向量数据库中文档的数量。
 * 该类实现了 DocumentCountProvider 接口，提供获取指定索引中向量总数的功能。
 */
@Component
public class PineconeDocumentChecker implements DocumentCountProvider {

    private final Pinecone pineconeClient;
    private final String indexName;

    /**
     * 构造函数，初始化 Pinecone 客户端和索引名称。
     *
     * @param pineconeClient Pinecone 客户端实例，用于与 Pinecone 服务进行交互
     * @param indexName 索引名称，从配置文件中读取
     */
    public PineconeDocumentChecker(Pinecone pineconeClient,
                                   @Value("${spring.ai.vectorstore.pinecone.index-name}") String indexName) {
        this.pineconeClient = pineconeClient;
        this.indexName = indexName;
    }

    /**
     * 获取 Pinecone 索引中的向量总数。
     *
     * @param indexName 要查询的索引名称
     * @return 索引中的向量总数；如果查询失败或索引不存在，则返回 0。
     */
    @Override
    public int getDocumentCount(String indexName) {
        try {
            // 获取指定索引的连接并查询索引统计信息
            Index index = pineconeClient.getIndexConnection(indexName);
            DescribeIndexStatsResponse stats = index.describeIndexStats();

            return stats.getTotalVectorCount();
        } catch (Exception e) {
            System.err.println("获取文档数量时出错: " + e.getMessage());
            return 0;
        }
    }

}


