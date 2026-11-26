from dataclasses import dataclass
from typing import Any
import requests


@dataclass
class AgentResult:
    answer: str
    retrieval_context: list[str]
    evidence_ids: list[str]
    plan: list[str]
    trace_id: str
    status: str
    grounded: bool


class AgentClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def chat(
        self,
        question: str,
        session_id: str,
        top_k: int = 8,
        timeout: int = 90,
    ) -> AgentResult:
        response = requests.post(
            f"{self.base_url}/api/agent/chat",
            json={
                "sessionId": session_id,
                "question": question,
                "topK": top_k,
            },
            timeout=timeout,
        )
        response.raise_for_status()
        payload: dict[str, Any] = response.json()

        evidence = payload.get("evidence", [])
        return AgentResult(
            answer=payload.get("answer", ""),
            retrieval_context=[
                item.get("content", "") for item in evidence
            ],
            evidence_ids=[
                item.get("documentId", "") for item in evidence
                if item.get("documentId")
            ],
            plan=payload.get("plan", []),
            trace_id=payload.get("traceId", ""),
            status=payload.get("status", ""),
            grounded=bool(payload.get("grounded", False)),
        )
