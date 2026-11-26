import json
from pathlib import Path
import requests

from src.config import get_config
from src.models.llm import SiliconFlowLLM
from src.services.agent_client import AgentClient
from src.services.agent_evaluator import AgentEvaluator
from src.services.trajectory_metrics import evaluate_trajectory


def load_cases(path: str) -> list[dict]:
    cases = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line.strip():
            cases.append(json.loads(line))
    return cases


def main():
    config = get_config()
    client = AgentClient(config.spring_ai.base_url)

    judge = SiliconFlowLLM(
        api_key=config.siliconflow.api_key,
        model_name=config.siliconflow.llm_model,
        base_url=config.siliconflow.base_url,
    )
    evaluator = AgentEvaluator(judge)

    reports = []

    for golden in load_cases("datasets/agent_golden.jsonl"):
        result = client.chat(
            question=golden["input"],
            session_id=f"eval-{golden['id']}",
            top_k=8,
        )

        answer_metrics = evaluator.evaluate_case(
            question=golden["input"],
            answer=result.answer,
            expected_output=golden.get("expected_output", ""),
            retrieval_context=result.retrieval_context,
        )

        citation_metrics = evaluator.citation_metrics(
            answer=result.answer,
            retrieved_ids=result.evidence_ids,
            expected_ids=golden.get("expected_document_ids", []),
        )

        trajectory_metrics = {}
        if result.trace_id:
            response = requests.get(
                f"{config.spring_ai.base_url.rstrip('/')}"
                f"/api/agent/tasks/{result.trace_id}",
                timeout=10,
            )
            if response.ok:
                trajectory_metrics = evaluate_trajectory(
                    response.json(),
                    max_tool_calls=golden.get("max_tool_calls", 4),
                )

        reports.append({
            "id": golden["id"],
            "trace_id": result.trace_id,
            "status": result.status,
            "grounded_flag": result.grounded,
            "answer_metrics": answer_metrics,
            "citation_metrics": citation_metrics,
            "trajectory_metrics": trajectory_metrics,
        })

    Path("agent_evaluation_report.json").write_text(
        json.dumps(reports, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
