package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.evaluation.FaithfulnessTestHelper;
import com.redis.demo.spring.ai.evaluation.FaithfulnessTestHelper.FaithfulnessTestCase;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 忠实性评估测试
 * 
 * 基于 TableRAG 论文 (arXiv:2506.10380) 的评估方法，
 * 测试 RAG 系统生成的回答是否忠实于检索到的上下文。
 * 
 * 忠实性(Faithfulness)定义：
 * 生成的回答完全基于提供的上下文信息，不包含幻觉或未经证实的声明。
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-evaluation.properties")
@EnabledIfEnvironmentVariable(named = "SILICONFLOW_KEY", matches = ".+")
public class FaithfulnessEvaluationTest {

    private static final Logger logger = LoggerFactory.getLogger(FaithfulnessEvaluationTest.class);

    @Autowired
    private ChatModel chatModel;

    @Value("classpath:test-data/faithfulness-test-cases.json")
    private Resource testCasesResource;

    @Value("${evaluation.chat.model}")
    private String evaluationModelName;

    @Value("${evaluation.factuality.threshold:0.8}")
    private double factualityThreshold;

    /**
     * 测试忠实回答应该通过忠实性检查
     */
    @Test
    @DisplayName("Evaluate Faithfulness - Faithful Answers Should Pass")
    public void evaluateFaithfulAnswers() {
        List<FaithfulnessTestCase> testCases = FaithfulnessTestHelper.loadTestCases(testCasesResource);

        int passCount = 0;
        int totalCases = testCases.size();

        for (FaithfulnessTestCase testCase : testCases) {
            logger.info("=== Evaluating Faithful Answer for TestCase: [{}] {} ===",
                    testCase.getId(), testCase.getScenario());
            logger.info("Question: {}", testCase.getQuestion());
            logger.info("Context: {}", testCase.getContext());
            logger.info("Faithful Answer: {}", testCase.getFaithfulAnswer());

            // 评估忠实回答的忠实性得分
            double faithfulnessScore = evaluateFaithfulness(
                    testCase.getContext(),
                    testCase.getFaithfulAnswer());

            logger.info("Faithfulness Score: {}", faithfulnessScore);

            if (faithfulnessScore >= factualityThreshold) {
                passCount++;
                logger.info("✅ PASSED - Faithful answer correctly identified as faithful");
            } else {
                logger.warn("❌ FAILED - Faithful answer was incorrectly marked as unfaithful");
            }
            logger.info("--------------------------------------------------");
        }

        double passRate = (double) passCount / totalCases;
        logger.info("Faithful Answers Test - Total Passed: {} / {} ({}%)",
                passCount, totalCases, String.format("%.1f", passRate * 100));

        assertTrue(passRate >= 0.7,
                "At least 70% of faithful answers should be correctly identified");
    }

    /**
     * 测试不忠实回答应该未通过忠实性检查
     */
    @Test
    @DisplayName("Evaluate Faithfulness - Unfaithful Answers Should Fail")
    public void evaluateUnfaithfulAnswers() {
        List<FaithfulnessTestCase> testCases = FaithfulnessTestHelper.loadTestCases(testCasesResource);

        int correctlyRejectedCount = 0;
        int totalCases = testCases.size();

        for (FaithfulnessTestCase testCase : testCases) {
            logger.info("=== Evaluating Unfaithful Answer for TestCase: [{}] {} ===",
                    testCase.getId(), testCase.getScenario());
            logger.info("Question: {}", testCase.getQuestion());
            logger.info("Context: {}", testCase.getContext());
            logger.info("Unfaithful Answer: {}", testCase.getUnfaithfulAnswer());

            // 评估不忠实回答的忠实性得分
            double faithfulnessScore = evaluateFaithfulness(
                    testCase.getContext(),
                    testCase.getUnfaithfulAnswer());

            logger.info("Faithfulness Score: {}", faithfulnessScore);

            if (faithfulnessScore < factualityThreshold) {
                correctlyRejectedCount++;
                logger.info("✅ PASSED - Unfaithful answer correctly identified as unfaithful");
            } else {
                logger.warn("❌ FAILED - Unfaithful answer was incorrectly marked as faithful");
            }
            logger.info("--------------------------------------------------");
        }

        double rejectRate = (double) correctlyRejectedCount / totalCases;
        logger.info("Unfaithful Answers Test - Correctly Rejected: {} / {} ({}%)",
                correctlyRejectedCount, totalCases, String.format("%.1f", rejectRate * 100));

        assertTrue(rejectRate >= 0.7,
                "At least 70% of unfaithful answers should be correctly rejected");
    }

    /**
     * 综合忠实性评估测试
     * 同时测试忠实和不忠实回答，计算区分能力
     */
    @Test
    @DisplayName("Evaluate Faithfulness - Discrimination Ability")
    public void evaluateDiscriminationAbility() {
        List<FaithfulnessTestCase> testCases = FaithfulnessTestHelper.loadTestCases(testCasesResource);

        int correctDiscriminations = 0;
        int totalCases = testCases.size();

        for (FaithfulnessTestCase testCase : testCases) {
            logger.info("=== Testing Discrimination for: [{}] {} ===",
                    testCase.getId(), testCase.getScenario());

            // 评估忠实回答
            double faithfulScore = evaluateFaithfulness(
                    testCase.getContext(),
                    testCase.getFaithfulAnswer());

            // 评估不忠实回答
            double unfaithfulScore = evaluateFaithfulness(
                    testCase.getContext(),
                    testCase.getUnfaithfulAnswer());

            logger.info("Faithful Answer Score: {}", faithfulScore);
            logger.info("Unfaithful Answer Score: {}", unfaithfulScore);

            // 检查是否能够正确区分：忠实回答的分数应高于不忠实回答
            if (faithfulScore > unfaithfulScore) {
                correctDiscriminations++;
                logger.info("✅ PASSED - Correctly discriminated faithful from unfaithful");
            } else {
                logger.warn("❌ FAILED - Failed to discriminate (faithful: {}, unfaithful: {})",
                        faithfulScore, unfaithfulScore);
            }
            logger.info("--------------------------------------------------");
        }

        double discriminationRate = (double) correctDiscriminations / totalCases;
        logger.info("Discrimination Test - Correct: {} / {} ({}%)",
                correctDiscriminations, totalCases, String.format("%.1f", discriminationRate * 100));

        assertTrue(discriminationRate >= 0.7,
                "Evaluator should correctly discriminate at least 70% of cases");
    }

    /**
     * 使用 LLM-as-a-Judge 方法评估忠实性
     * 
     * @param context 提供的上下文
     * @param answer  待评估的回答
     * @return 忠实性得分 (0.0 - 1.0)
     */
    private double evaluateFaithfulness(String context, String answer) {
        String promptText = """
                You are an impartial evaluator assessing the faithfulness of an AI-generated answer.

                Faithfulness measures whether the answer is entirely grounded in and supported by the provided context.
                An answer is faithful if:
                1. All claims in the answer can be directly inferred from the context
                2. The answer does not contain information not present in the context
                3. The answer does not contradict the context

                Context:
                %s

                Answer to evaluate:
                %s

                Evaluate the faithfulness of the answer on a scale from 0 to 10:
                - 0-3: Unfaithful (contains hallucinations or unsupported claims)
                - 4-6: Partially faithful (some claims supported, some not)
                - 7-10: Faithful (all claims are supported by the context)

                Respond with ONLY a single number from 0 to 10. Do not include any explanation.
                """.formatted(context, answer);

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
            logger.warn("Could not parse faithfulness score from response: {}", content);
            // 如果无法解析，尝试检测关键词
            String lowerContent = content.toLowerCase();
            if (lowerContent.contains("faithful") && !lowerContent.contains("unfaithful")) {
                return 0.8;
            } else if (lowerContent.contains("unfaithful") || lowerContent.contains("hallucination")) {
                return 0.2;
            }
            return 0.5; // 默认中间值
        }
    }
}
