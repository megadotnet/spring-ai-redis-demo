package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.evaluation.RagEvaluationHelper;
import com.redis.demo.spring.ai.evaluation.RagEvaluationHelper.TestCase;
import com.redis.demo.spring.ai.service.RagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-evaluation.properties")
@EnabledIfEnvironmentVariable(named = "SILICONFLOW_KEY", matches = ".+")
public class RagEvaluationTest {

    private static final Logger logger = LoggerFactory.getLogger(RagEvaluationTest.class);

    @Autowired
    private RagService ragService;

    @Autowired
    private ChatModel chatModel;

    @Value("classpath:test-data/rag-test-cases.json")
    private Resource testCasesResource;

    @Value("${evaluation.chat.model}")
    private String evaluationModelName;

    @Test
    @DisplayName("Evaluate RAG Relevance for TableRAG Paper")
    public void evaluateRelevance() {
        List<TestCase> testCases = RagEvaluationHelper.loadTestCases(testCasesResource);

        int passCount = 0;
        for (TestCase testCase : testCases) {
            logger.info("Evaluating TestCase: [{}] {}", testCase.getId(), testCase.getScenario());
            logger.info("Question: {}", testCase.getQuestion());

            // 1. Call RAG service to get answer
            Generation generation = ragService.retrieve(testCase.getQuestion());
            String answer = generation.getOutput().getText();
            logger.info("RAG Answer: {}", answer);

            // 2. Custom Evaluation Logic using the same ChatModel but with evaluation model
            // options
            boolean isRelevant = evaluateRelevance(testCase.getQuestion(), answer);

            if (isRelevant) {
                passCount++;
                logger.info("✅ PASSED");
            } else {
                logger.warn("❌ FAILED: The answer is not considered relevant.");
            }
            logger.info("--------------------------------------------------");
        }

        logger.info("Total Passed: {} / {}", passCount, testCases.size());
        assertTrue(passCount > 0, "At least one test case should pass");
    }

    private boolean evaluateRelevance(String question, String answer) {
        String promptText = """
                You are an impartial evaluator evaluating the relevance of an AI assistant's answer to a user's question.

                Question: %s
                Answer: %s

                Is the answer relevant to the question? Answer with 'YES' or 'NO' only.
                """
                .formatted(question, answer);

        // Use specific model for evaluation if possible, reusing the injected ChatModel
        Prompt prompt = new Prompt(new UserMessage(promptText),
                OpenAiChatOptions.builder()
                        .model(evaluationModelName)
                        .temperature(0.0)
                        .build());

        ChatResponse response = chatModel.call(prompt);
        String content = response.getResult().getOutput().getText().trim().toUpperCase();
        logger.info("Evaluator response: {}", content);
        return content.contains("YES");
    }
}
