import re
from typing import Any
from deepeval.metrics import (
    ContextualRecallMetric,
    FaithfulnessMetric,
    GEval,
)
from deepeval.test_case import LLMTestCase, LLMTestCaseParams


class AgentEvaluator:
    def __init__(self, judge_model, thresholds: dict[str, float] | None = None):
        self.judge_model = judge_model
        self.thresholds = thresholds or {
            "contextual_recall": 0.70,
            "faithfulness": 0.75,
            "correctness": 0.75,
        }

        self.context_recall = ContextualRecallMetric(
            threshold=self.thresholds["contextual_recall"],
            model=judge_model,
            include_reason=True,
        )
        self.faithfulness = FaithfulnessMetric(
            threshold=self.thresholds["faithfulness"],
            model=judge_model,
            include_reason=True,
        )
        self.correctness = GEval(
            name="Correctness",
            criteria=(
                "判断 actual_output 是否准确回答 input，"
                "并与 expected_output 中的关键事实一致。"
                "不要求措辞相同；若答案与证据冲突或编造关键事实，应判低分。"
            ),
            evaluation_params=[
                LLMTestCaseParams.INPUT,
                LLMTestCaseParams.ACTUAL_OUTPUT,
                LLMTestCaseParams.EXPECTED_OUTPUT,
                LLMTestCaseParams.RETRIEVAL_CONTEXT,
            ],
            threshold=self.thresholds["correctness"],
            model=judge_model,
            include_reason=True,
        )

    @staticmethod
    def citation_metrics(
        answer: str,
        retrieved_ids: list[str],
        expected_ids: list[str],
    ) -> dict[str, Any]:
        cited_ids = set(re.findall(r"\[证据\s+([^\]\s]+)\]", answer or ""))
        retrieved = set(retrieved_ids or [])
        expected = set(expected_ids or [])

        citation_precision = (
            len(cited_ids & retrieved) / len(cited_ids)
            if cited_ids else 0.0
        )
        evidence_hit_recall = (
            len(expected & retrieved) / len(expected)
            if expected else None
        )
        invalid_citations = sorted(cited_ids - retrieved)

        return {
            "citation_count": len(cited_ids),
            "citation_precision": citation_precision,
            "evidence_hit_recall": evidence_hit_recall,
            "invalid_citations": invalid_citations,
        }

    def evaluate_case(
        self,
        question: str,
        answer: str,
        expected_output: str,
        retrieval_context: list[str],
    ) -> dict[str, Any]:
        case = LLMTestCase(
            input=question,
            actual_output=answer,
            expected_output=expected_output,
            retrieval_context=retrieval_context,
        )

        metrics = [
            self.context_recall,
            self.faithfulness,
            self.correctness,
        ]

        results: dict[str, Any] = {}
        for metric in metrics:
            metric.measure(case)
            results[metric.__class__.__name__] = {
                "score": metric.score,
                "success": metric.success,
                "reason": getattr(metric, "reason", None),
            }

        return results
