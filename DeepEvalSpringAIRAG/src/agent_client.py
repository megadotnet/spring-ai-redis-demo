"""
Agent 评估客户端（DeepEval 集成入口）
=====================================
调用 Spring AI 新增的 /api/agent/chat 端点，把回答、证据和计划写入评估样本。

与旧的 /retrieve 评估入口的区别：
- /retrieve 只返回检索结果，其 "actual_output" 是拼接后的检索内容，
  不能称为端到端答案质量分数；
- 本客户端拿到的是真实最终答案 (actual_output)、真实检索上下文
  (retrieval_context)、引用 ID (evidence_ids) 和 Agent 工具轨迹 (plan)，
  可分别用于：
    * 检索质量：Contextual Recall / Precision
    * 答案质量：Faithfulness / Correctness
    * 引用质量：citation grounding（校验引用 ID 是否存在且支持结论）
    * Agent 执行质量：任务完成、是否重复检索、失败降级行为

注意：ID hit rate 评估必须传入非空的 expected IDs（来自人工确认的 golden 集），
修正旧脚本中收到空 expected ID 列表的问题。
"""

import requests


def run_agent(base_url: str, session_id: str, question: str, top_k: int = 8):
    """调用 Agent 端点并构造一条评估记录。

    :param base_url: Spring AI 服务地址，例如 http://localhost:8080
    :param session_id: 会话 ID（评估时建议每条样本使用独立 session，避免历史污染）
    :param question: 用户问题
    :param top_k: 每次检索候选数量
    :return: 可直接喂给 DeepEval 各 metric 的字典
    """
    response = requests.post(
        f"{base_url}/api/agent/chat",
        json={
            "sessionId": session_id,
            "question": question,
            "topK": top_k,
        },
        timeout=90,
    )
    response.raise_for_status()
    payload = response.json()

    return {
        "input": question,
        "actual_output": payload["answer"],
        "retrieval_context": [
            item["content"] for item in payload.get("evidence", [])
        ],
        "evidence_ids": [
            item["documentId"] for item in payload.get("evidence", [])
        ],
        "trace_id": payload.get("traceId"),
        "plan": payload.get("plan", []),
        "grounded": payload.get("grounded", False),
    }
