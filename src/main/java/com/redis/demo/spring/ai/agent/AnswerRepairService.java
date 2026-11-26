package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;

import java.util.List;

/**
 * 答案修正服务接口。
 * 修正只基于现有证据，不得生成新事实或新引用。
 */
public interface AnswerRepairService {

    /**
     * 修正答案。
     *
     * @param question       用户原始问题
     * @param previousAnswer 前一次生成的答案
     * @param evidence       可用的检索证据
     * @param checkerFeedback 校验器反馈说明
     * @return 修正后的答案文本
     */
    String repair(
            String question,
            String previousAnswer,
            List<Evidence> evidence,
            String checkerFeedback
    );
}
