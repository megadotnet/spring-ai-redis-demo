import math
from typing import Iterable


def recall_at_k(
    relevant_ids: Iterable[str],
    retrieved_ids: list[str],
    k: int,
) -> float | None:
    relevant = set(relevant_ids)
    if not relevant:
        return None
    return len(relevant & set(retrieved_ids[:k])) / len(relevant)


def reciprocal_rank(
    relevant_ids: Iterable[str],
    retrieved_ids: list[str],
) -> float | None:
    relevant = set(relevant_ids)
    if not relevant:
        return None
    for rank, doc_id in enumerate(retrieved_ids, start=1):
        if doc_id in relevant:
            return 1.0 / rank
    return 0.0


def ndcg_at_k(
    relevance_by_id: dict[str, int],
    retrieved_ids: list[str],
    k: int,
) -> float | None:
    if not relevance_by_id:
        return None

    def dcg(scores: list[int]) -> float:
        return sum(
            (2 ** score - 1) / math.log2(index + 2)
            for index, score in enumerate(scores)
        )

    actual = [
        relevance_by_id.get(doc_id, 0)
        for doc_id in retrieved_ids[:k]
    ]
    ideal = sorted(relevance_by_id.values(), reverse=True)[:k]

    ideal_dcg = dcg(ideal)
    return 0.0 if ideal_dcg == 0 else dcg(actual) / ideal_dcg
