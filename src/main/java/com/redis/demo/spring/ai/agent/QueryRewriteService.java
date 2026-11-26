package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;

import java.util.List;

/**
 * Query Rewrite 服务接口：根据原始问题、已有证据和校验反馈生成补充检索 query。
 * 此接口不负责递归检索，Runtime 会限制调用次数。
 */
public interface QueryRewriteService {

    /**
     * 生成补充检索查询。
     *
     * @param originalQuestion 用户原始问题
     * @param currentEvidence  当前已有的证据
     * @param checkerFeedback  校验器反馈说明
     * @return 补充查询列表（失败时返回空列表）
     */
    List<String> rewrite(
            String originalQuestion,
            List<Evidence> currentEvidence,
            String checkerFeedback
    );
}
