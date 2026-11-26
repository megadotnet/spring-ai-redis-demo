package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.evaluation.CorrectnessTestHelper;
import com.redis.demo.spring.ai.evaluation.CorrectnessTestHelper.CorrectnessTestCase;
import com.redis.demo.spring.ai.evaluation.CorrectnessTestHelper.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 正确性评估测试
 * 
 * 综合"相关性"和"事实性"评估，给出整体的正确性分数。
 * 基于 TableRAG 论文 (arXiv:2506.10380) 的评估方法。
 * 
 * 正确性(Correctness)定义：
 * 正确性 = 相关性权重(0.4) × 相关性得分 + 事实性权重(0.6) × 事实性得分
 * 
 * - 相关性(Relevance): 回答是否与问题相关
 * - 事实性(Faithfulness): 回答是否忠实于提供的上下文
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-evaluation.properties")
@EnabledIfEnvironmentVariable(named = "SILICONFLOW_KEY", matches = ".+")
public class CorrectnessEvaluationTest {

    private static final Logger logger = LoggerFactory.getLogger(CorrectnessEvaluationTest.class);

    // 相关性在正确性计算中的权重
    private static final double RELEVANCE_WEIGHT = 0.4;
    // 事实性在正确性计算中的权重
    private static final double FAITHFULNESS_WEIGHT = 0.6;

    @Autowired
    private ChatModel chatModel;

    @Value("classpath:test-data/correctness-test-cases.json")
    private Resource testCasesResource;

    @Value("${evaluation.chat.model}")
    private String evaluationModelName;

    @Value("${evaluation.relevance.threshold:0.7}")
    private double relevanceThreshold;

    @Value("${evaluation.factuality.threshold:0.8}")
    private double factualityThreshold;

    /**
     * 综合正确性评估测试
     * 对每个测试用例的期望答案进行相关性和事实性评估，计算综合正确性得分
     */
    @Test
    @DisplayName("Evaluate Correctness - Combined Relevance and Faithfulness")
    public void evaluateCorrectness() {
        List<CorrectnessTestCase> testCases = CorrectnessTestHelper.loadTestCases(testCasesResource);
        List<EvaluationResult> results = new ArrayList<>();

        int passCount = 0;
        double totalCorrectness = 0.0;

        for (CorrectnessTestCase testCase : testCases) {
            logger.info("=== Evaluating Correctness for TestCase: [{}] {} ===",
                    testCase.getId(), testCase.getScenario());
            logger.info("Question: {}", testCase.getQuestion());
            logger.info("Context: {}", testCase.getContext());
            logger.info("Expected Answer: {}", testCase.getExpectedAnswer());

            // 1. 评估相关性：回答是否与问题相关
            double relevanceScore = evaluateRelevance(
                    testCase.getQuestion(),
                    testCase.getExpectedAnswer());
            logger.info("Relevance Score: {}", String.format("%.2f", relevanceScore));

            // 2. 评估事实性：回答是否忠实于上下文
            double faithfulnessScore = evaluateFaithfulness(
                    testCase.getContext(),
                    testCase.getExpectedAnswer());
            logger.info("Faithfulness Score: {}", String.format("%.2f", faithfulnessScore));

            // 3. 计算综合正确性得分
            EvaluationResult result = new EvaluationResult(relevanceScore, faithfulnessScore);
            results.add(result);

            double correctnessScore = result.getCorrectnessScore();
            totalCorrectness += correctnessScore;

            logger.info("Correctness Score: {} (Relevance×{} + Faithfulness×{})",
                    String.format("%.2f", correctnessScore), RELEVANCE_WEIGHT, FAITHFULNESS_WEIGHT);

            // 判断是否通过（正确性得分需要大于0.7）
            double correctnessThreshold = RELEVANCE_WEIGHT * relevanceThreshold +
                    FAITHFULNESS_WEIGHT * factualityThreshold;
            if (correctnessScore >= correctnessThreshold) {
                passCount++;
                logger.info("✅ PASSED");
            } else {
                logger.warn("❌ FAILED - Correctness score below threshold {}",
                        String.format("%.2f", correctnessThreshold));
            }
            logger.info("--------------------------------------------------");
        }

        // 输出汇总统计
        double avgCorrectness = totalCorrectness / testCases.size();
        double passRate = (double) passCount / testCases.size();

        logger.info("========== Correctness Evaluation Summary ==========");
        logger.info("Total Test Cases: {}", testCases.size());
        logger.info("Passed: {} ({}%)", passCount, String.format("%.1f", passRate * 100));
        logger.info("Average Correctness Score: {}", String.format("%.2f", avgCorrectness));
        logger.info("====================================================");

        assertTrue(passRate >= 0.7,
                "At least 70% of test cases should pass correctness evaluation");
    }

    /**
     * 详细评估测试：分别展示各维度得分
     */
    @Test
    @DisplayName("Evaluate Correctness - Detailed Scores")
    public void evaluateDetailedScores() {
        List<CorrectnessTestCase> testCases = CorrectnessTestHelper.loadTestCases(testCasesResource);

        logger.info("========== Detailed Correctness Evaluation ==========");
        logger.info(String.format("%-12s %-20s %-12s %-15s %-12s",
                "ID", "Scenario", "Relevance", "Faithfulness", "Correctness"));
        logger.info("--------------------------------------------------------------");

        double totalRelevance = 0.0;
        double totalFaithfulness = 0.0;
        double totalCorrectness = 0.0;

        for (CorrectnessTestCase testCase : testCases) {
            double relevanceScore = evaluateRelevance(
                    testCase.getQuestion(),
                    testCase.getExpectedAnswer());

            double faithfulnessScore = evaluateFaithfulness(
                    testCase.getContext(),
                    testCase.getExpectedAnswer());

            double correctnessScore = RELEVANCE_WEIGHT * relevanceScore +
                    FAITHFULNESS_WEIGHT * faithfulnessScore;

            totalRelevance += relevanceScore;
            totalFaithfulness += faithfulnessScore;
            totalCorrectness += correctnessScore;

            logger.info(String.format("%-12s %-20s %-12.2f %-15.2f %-12.2f",
                    testCase.getId(),
                    testCase.getScenario().length() > 18 ? testCase.getScenario().substring(0, 18) + ".."
                            : testCase.getScenario(),
                    relevanceScore,
                    faithfulnessScore,
                    correctnessScore));
        }

        int count = testCases.size();
        logger.info("--------------------------------------------------------------");
        logger.info(String.format("%-12s %-20s %-12.2f %-15.2f %-12.2f",
                "AVERAGE", "",
                totalRelevance / count,
                totalFaithfulness / count,
                totalCorrectness / count));
        logger.info("==============================================================");

        assertTrue(totalCorrectness / count >= 0.5,
                "Average correctness score should be at least 0.5");
    }

    /**
     * 评估相关性：回答是否与问题相关
     * 
     * @param question 用户问题
     * @param answer   待评估的回答
     * @return 相关性得分 (0.0 - 1.0)
     */
    private double evaluateRelevance(String question, String answer) {
        String promptText = """
                You are an impartial evaluator assessing whether an answer is relevant to a question.

                Relevance measures whether the answer addresses the question asked.
                An answer is relevant if:
                1. It directly addresses the topic of the question
                2. It provides information that helps answer the question
                3. It does not go off-topic or provide unrelated information

                Question: %s

                Answer: %s

                Evaluate the relevance of the answer on a scale from 0 to 10:
                - 0-3: Not relevant (off-topic or unrelated)
                - 4-6: Partially relevant (some relevant content)
                - 7-10: Highly relevant (directly addresses the question)

                Respond with ONLY a single number from 0 to 10. Do not include any explanation.
                """.formatted(question, answer);

        return evaluateWithLLM(promptText);
    }

    /**
     * 评估事实性：回答是否忠实于上下文
     * 
     * @param context 提供的上下文
     * @param answer  待评估的回答
     * @return 事实性得分 (0.0 - 1.0)
     */
    private double evaluateFaithfulness(String context, String answer) {
        String promptText = """
                You are an impartial evaluator assessing the faithfulness of an answer to a given context.

                Faithfulness measures whether the answer is entirely grounded in the provided context.
                An answer is faithful if:
                1. All claims in the answer can be directly inferred from the context
                2. The answer does not contain information not present in the context
                3. The answer does not contradict the context

                Context: %s

                Answer: %s

                Evaluate the faithfulness of the answer on a scale from 0 to 10:
                - 0-3: Unfaithful (contains hallucinations or unsupported claims)
                - 4-6: Partially faithful (some claims supported, some not)
                - 7-10: Faithful (all claims are supported by the context)

                Respond with ONLY a single number from 0 to 10. Do not include any explanation.
                """.formatted(context, answer);

        return evaluateWithLLM(promptText);
    }

    /**
     * 使用 LLM 进行评估
     * 
     * @param promptText 评估提示词
     * @return 归一化得分 (0.0 - 1.0)
     */
    private double evaluateWithLLM(String promptText) {
        Prompt prompt = new Prompt(
                new UserMessage(promptText),
                OpenAiChatOptions.builder()
                        .model(evaluationModelName)
                        .temperature(0.0)
                        .build());

        ChatResponse response = chatModel.call(prompt);
        String content = response.getResult().getOutput().getText().trim();

        logger.debug("Evaluator raw response: {}", content);

        try {
            // 解析数字得分
            double score = Double.parseDouble(content.replaceAll("[^0-9.]", ""));
            // 归一化到 0-1 范围
            return Math.min(1.0, Math.max(0.0, score / 10.0));
        } catch (NumberFormatException e) {
            logger.warn("Could not parse score from response: {}", content);
            return 0.5; // 默认中间值
        }
    }
}
