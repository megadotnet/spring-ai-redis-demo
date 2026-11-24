package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;

import java.util.List;

/**
 * 检索工具统一契约。
 * Agent Runtime 通过该接口调用服务端受控检索能力，
 * 不直接接触 Lucene/Milvus SDK 细节，也不执行模型任意拼接的 URL 或命令。
 */
public interface RetrievalTool {

    /**
     * 执行一次检索（含可选精排），返回带稳定 ID 的证据候选。
     *
     * @param query 检索查询
     * @param topK  期望返回的候选数量
     * @return 证据列表；无结果时返回空列表而非 null
     */
    List<Evidence> retrieve(String query, int topK);
}
