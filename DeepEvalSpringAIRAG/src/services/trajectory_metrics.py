from typing import Any


def evaluate_trajectory(
    task_state: dict[str, Any],
    max_tool_calls: int = 4,
) -> dict[str, Any]:
    events = task_state.get("events", [])

    retrieval_events = [
        event for event in events
        if event.get("toolName") == "hybrid_retrieve"
    ]

    query_hashes = [
        event.get("queryHash")
        for event in retrieval_events
        if event.get("queryHash")
    ]

    failures = [
        event for event in retrieval_events
        if not event.get("success", False)
    ]

    unique_queries = set(query_hashes)
    duplicate_query_count = len(query_hashes) - len(unique_queries)

    return {
        "tool_calls": len(retrieval_events),
        "tool_call_budget_passed": len(retrieval_events) <= max_tool_calls,
        "tool_failure_count": len(failures),
        "duplicate_query_count": duplicate_query_count,
        "task_status": task_state.get("finalStatus"),
    }
