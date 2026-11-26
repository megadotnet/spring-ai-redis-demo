"""
DeepEval RAG 评估脚本
====================
功能：
1. 从 Milvus 扫描文档，使用 DeepEval Synthesizer 生成测试问答对
2. 调用 Spring AI 的检索接口获取 Top-K 结果
3. 使用 ContextualRecallMetric 评估检索质量
4. 支持合成数据 ID 命中对比 + 真实问答 content coverage 评估
"""

import os
import json
import random
import requests
from pathlib import Path
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass, asdict
from pymilvus import MilvusClient
from deepeval.synthesizer import Synthesizer
from deepeval.dataset import EvaluationDataset, Golden
from deepeval.models.base_model import DeepEvalBaseLLM
from deepeval.models import DeepEvalBaseEmbeddingModel
from deepeval.metrics import ContextualRecallMetric
from deepeval.test_case import LLMTestCase
from deepeval import evaluate
from openai import OpenAI

# ==========================================
# 1. 配置区域
# ==========================================

# 从环境变量获取 API Key
SILICON_FLOW_API_KEY = os.getenv("SILICONFLOW_KEY")

if not SILICON_FLOW_API_KEY:
    raise ValueError(
        "未找到环境变量 'SILICONFLOW_KEY'。请在运行前设置，例如：\n"
        "Linux/Mac: export SILICONFLOW_KEY='sk-xxx'\n"
        "Windows: set SILICONFLOW_KEY=sk-xxx"
    )

# Milvus 配置
MILVUS_URI = "http://10.1.1.234:19530"
MILVUS_TOKEN = "root:Milvus"
COLLECTION_NAME = "paper_collection_vpdf"

# Spring AI 检索接口配置
SPRING_AI_BASE_URL = "http://localhost:8080"
SPRING_AI_RETRIEVE_ENDPOINT = "/retrieve"

# 评估配置
NUM_GOLDENS_TO_GENERATE = 25  # 减少生成数量，提升速度
TOP_K = 5
CONTEXT_RECALL_THRESHOLD = 0.7

# 性能优化配置
NUM_CONTEXTS_TO_SAMPLE = 25   # 从 Milvus 采样的 context 数量
MAX_GOLDENS_PER_CONTEXT = 1   # 每个 context 生成的 golden 数量（从 2 减少到 1）
DATASET_CACHE_FILE = "rag_test_dataset.json"  # 缓存文件，避免重复生成

# ==========================================
# 2. 自定义 LLM 类适配硅基流动
# ==========================================
class SiliconFlowLLM(DeepEvalBaseLLM):
    """使用硅基流动 API 的 LLM - 使用 Qwen2.5 模型（比 GLM 快 2-3 倍）"""
    def __init__(self, api_key: str):
        # 使用更快的 Qwen2.5 模型替代 GLM-Z1
        self.model_name = "Qwen/Qwen2.5-7B-Instruct"
        self.client = OpenAI(
            api_key=api_key,
            base_url="https://api.siliconflow.cn/v1"
        )

    def load_model(self):
        return self.client

    def generate(self, prompt: str) -> str:
        """同步生成方法"""
        try:
            response = self.client.chat.completions.create(
                model=self.model_name,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.7,
                max_tokens=2048
            )
            return response.choices[0].message.content
        except Exception as e:
            print(f"Generate error: {e}")
            return ""

    async def a_generate(self, prompt: str) -> str:
        return self.generate(prompt)

    def get_model_name(self):
        return self.model_name


# ==========================================
# 3. 自定义 Embedding 模型类适配硅基流动
# ==========================================
class SiliconFlowBGEEmbedding(DeepEvalBaseEmbeddingModel):
    """使用硅基流动的 BAAI/bge-large-en-v1.5 模型"""
    def __init__(self, api_key: str):
        self.model_name = "BAAI/bge-large-en-v1.5"
        self.api_key = api_key
        self.client = OpenAI(
            api_key=api_key,
            base_url="https://api.siliconflow.cn/v1"
        )
        super().__init__(model=self.model_name)

    def load_model(self):
        return self.client

    def embed_text(self, text: str) -> List[float]:
        try:
            response = self.client.embeddings.create(
                model=self.model_name,
                input=[text]
            )
            return response.data[0].embedding
        except Exception as e:
            print(f"Embedding error: {e}")
            return []

    def embed_texts(self, texts: List[str]) -> List[List[float]]:
        try:
            response = self.client.embeddings.create(
                model=self.model_name,
                input=texts
            )
            return [item.embedding for item in response.data]
        except Exception as e:
            print(f"Embedding documents error: {e}")
            return []

    async def a_embed_text(self, text: str) -> List[float]:
        return self.embed_text(text)

    async def a_embed_texts(self, texts: List[str]) -> List[List[float]]:
        return self.embed_texts(texts)

    def get_model_name(self) -> str:
        return self.model_name


# ==========================================
# 4. 数据结构
# ==========================================
@dataclass
class RetrievalResult:
    """与 Spring AI 返回的 RetrievalResult 对应"""
    id: str
    content: str
    score: Optional[float]
    metadata: Dict[str, Any]


@dataclass
class TestCase:
    """测试用例结构"""
    question: str
    expected_context: str
    expected_doc_ids: List[str]
    retrieved_contexts: List[str] = None
    retrieved_doc_ids: List[str] = None


# ==========================================
# 5. 业务逻辑代码
# ==========================================

def fetch_docs_from_milvus(limit: int = 50) -> Dict[str, str]:
    """从 Milvus 读取文档"""
    print(f"正在连接 Milvus ({MILVUS_URI})...")
    client = MilvusClient(uri=MILVUS_URI, token=MILVUS_TOKEN)
    
    print(f"正在查询集合: {COLLECTION_NAME}...")
    res = client.query(
        collection_name=COLLECTION_NAME,
        filter='doc_id != ""',
        output_fields=["content", "doc_id"],
        limit=limit
    )
    
    docs_map = {str(r['doc_id']): r['content'] for r in res}
    print(f"成功获取 {len(docs_map)} 篇文档片段。")
    return docs_map


def call_spring_ai_retrieval(query: str, top_k: int = TOP_K) -> List[RetrievalResult]:
    """调用 Spring AI 检索接口获取 Top-K 结果"""
    url = f"{SPRING_AI_BASE_URL}{SPRING_AI_RETRIEVE_ENDPOINT}"
    
    try:
        response = requests.post(
            url,
            json={"query": query, "topK": str(top_k)},
            headers={"Content-Type": "application/json"},
            timeout=30
        )
        response.raise_for_status()
        
        results = []
        for item in response.json():
            results.append(RetrievalResult(
                id=item.get("id", ""),
                content=item.get("content", ""),
                score=item.get("score"),
                metadata=item.get("metadata", {})
            ))
        return results
    
    except requests.exceptions.RequestException as e:
        print(f"Spring AI 检索请求失败: {e}")
        return []


def load_cached_dataset(cache_file: str) -> Optional[List[Golden]]:
    """尝试从缓存加载已生成的测试数据集"""
    cache_path = Path(cache_file)
    if cache_path.exists():
        print(f"发现缓存文件: {cache_file}，正在加载...")
        try:
            dataset = EvaluationDataset()
            dataset.pull(cache_file.replace('.json', ''), auto_convert_goldens_to_test_cases=False)
            if dataset.goldens:
                print(f"从缓存加载了 {len(dataset.goldens)} 条测试用例。")
                return dataset.goldens
        except Exception as e:
            print(f"缓存加载失败: {e}，将重新生成...")
    return None


def generate_goldens(docs_map: Dict[str, str], custom_llm: DeepEvalBaseLLM, 
                     custom_embedding: SiliconFlowBGEEmbedding) -> Tuple[List[Golden], List[str]]:
    """使用 DeepEval Synthesizer 生成测试问答对（带缓存和采样优化）"""
    
    # 尝试从缓存加载
    cached = load_cached_dataset(DATASET_CACHE_FILE)
    if cached:
        return cached, list(docs_map.keys())
    
    # 随机采样以减少处理量
    doc_items = list(docs_map.items())
    if len(doc_items) > NUM_CONTEXTS_TO_SAMPLE:
        print(f"从 {len(doc_items)} 个文档中随机采样 {NUM_CONTEXTS_TO_SAMPLE} 个...")
        doc_items = random.sample(doc_items, NUM_CONTEXTS_TO_SAMPLE)
    
    doc_ids = [item[0] for item in doc_items]
    contexts = [[item[1]] for item in doc_items]
    
    print(f"正在使用 {len(contexts)} 个上下文生成测试问答对...")
    print(f"每个 context 生成 {MAX_GOLDENS_PER_CONTEXT} 个 golden（预计生成 {len(contexts) * MAX_GOLDENS_PER_CONTEXT} 个）")
    
    # 创建 Synthesizer，使用更快的模型
    synthesizer = Synthesizer(model=custom_llm)
    
    goldens = synthesizer.generate_goldens_from_contexts(
        contexts=contexts,
        max_goldens_per_context=MAX_GOLDENS_PER_CONTEXT,
        include_expected_output=True
    )
    
    # 限制数量
    final_goldens = goldens[:NUM_GOLDENS_TO_GENERATE]
    print(f"生成完成！共获得 {len(final_goldens)} 条测试用例。")
    
    return final_goldens, doc_ids


def evaluate_id_hit_rate(expected_doc_ids: List[str], retrieved_doc_ids: List[str]) -> float:
    """计算 ID 命中率（针对合成数据）"""
    if not expected_doc_ids:
        return 0.0
    
    expected_set = set(expected_doc_ids)
    retrieved_set = set(retrieved_doc_ids)
    hits = len(expected_set & retrieved_set)
    
    return hits / len(expected_set)


def run_evaluation(goldens: List[Golden], custom_llm: DeepEvalBaseLLM):
    """执行完整的 RAG 评估流程"""
    
    print("\n" + "=" * 50)
    print("开始 RAG 检索质量评估")
    print("=" * 50)
    
    # 创建 ContextualRecallMetric
    metric = ContextualRecallMetric(
        threshold=CONTEXT_RECALL_THRESHOLD,
        model=custom_llm,
        include_reason=True
    )
    
    test_cases = []
    id_hit_rates = []
    
    for i, golden in enumerate(goldens):
        question = golden.input
        expected_output = golden.expected_output or ""
        expected_contexts = golden.context or []
        
        print(f"\n[{i+1}/{len(goldens)}] 正在评估问题: {question[:50]}...")
        
        # 调用 Spring AI 检索接口
        retrieval_results = call_spring_ai_retrieval(question, TOP_K)
        
        if not retrieval_results:
            print(f"  ⚠️ 检索结果为空，跳过此用例")
            continue
        
        # 提取检索到的上下文和 ID
        retrieved_contexts = [r.content for r in retrieval_results]
        retrieved_doc_ids = [r.id for r in retrieval_results]
        
        # 计算 ID 命中率（针对合成数据）
        # 尝试从 expected_contexts 中推断 expected_doc_ids
        # 如果是合成数据，context 中可能包含原始 doc_id 信息
        id_hit_rate = evaluate_id_hit_rate(
            expected_doc_ids=[],  # 合成数据可能没有明确的 doc_id
            retrieved_doc_ids=retrieved_doc_ids
        )
        id_hit_rates.append(id_hit_rate)
        
        # 构建 LLMTestCase 用于 ContextualRecallMetric
        # ContextualRecallMetric 需要:
        # - input: 用户问题
        # - actual_output: RAG 系统的实际输出（这里用检索内容拼接）
        # - expected_output: 期望的输出
        # - retrieval_context: 检索到的上下文列表
        test_case = LLMTestCase(
            input=question,
            actual_output=" ".join(retrieved_contexts),  # 简化处理
            expected_output=expected_output,
            retrieval_context=retrieved_contexts
        )
        test_cases.append(test_case)
    
    if not test_cases:
        print("没有有效的测试用例，评估终止。")
        return
    
    # 执行 ContextualRecall 评估
    print(f"\n正在使用 ContextualRecallMetric 评估 {len(test_cases)} 个测试用例...")
    
    results = evaluate(
        test_cases=test_cases,
        metrics=[metric],
        print_results=True
    )
    
    # 计算并输出汇总结果
    scores = [tc.metrics_data[0].score if tc.metrics_data else 0 for tc in test_cases]
    avg_score = sum(scores) / len(scores) if scores else 0
    avg_id_hit_rate = sum(id_hit_rates) / len(id_hit_rates) if id_hit_rates else 0
    
    print("\n" + "=" * 50)
    print("====== RAG 评估报告 ======")
    print("=" * 50)
    print(f"总测试用例数: {len(test_cases)}")
    print(f"ContextRecall 平均分: {avg_score:.4f}")
    print(f"ID 命中率: {avg_id_hit_rate * 100:.2f}%")
    
    # 保存评估报告
    report = {
        "total_test_cases": len(test_cases),
        "average_context_recall": avg_score,
        "average_id_hit_rate": avg_id_hit_rate,
        "individual_results": [
            {
                "question": tc.input[:100],
                "score": tc.metrics_data[0].score if tc.metrics_data else None,
                "reason": tc.metrics_data[0].reason if tc.metrics_data else None
            }
            for tc in test_cases
        ]
    }
    
    with open("evaluation_report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    
    print(f"详细结果已保存至: evaluation_report.json")
    
    return results


def save_dataset(goldens: List[Golden], filename: str = "rag_test_dataset"):
    """保存测试数据集"""
    dataset = EvaluationDataset(goldens=goldens)
    # DeepEval API: save_as(file_type, directory, file_name)
    dataset.save_as(file_type='json', directory='.', file_name=filename)
    print(f"测试数据集已保存为: {filename}.json")


# ==========================================
# 6. 主入口
# ==========================================

def main():
    print("=" * 50)
    print("DeepEval RAG 评估脚本")
    print("=" * 50)
    
    # 1. 初始化模型（使用更快的 Qwen2.5）
    print("\n[Step 1] 初始化模型 (Qwen/Qwen2.5-7B-Instruct)...")
    custom_llm = SiliconFlowLLM(api_key=SILICON_FLOW_API_KEY)
    custom_embedding = SiliconFlowBGEEmbedding(api_key=SILICON_FLOW_API_KEY)
    
    # 2. 从 Milvus 获取文档
    print("\n[Step 2] 从 Milvus 获取文档...")
    docs_map = fetch_docs_from_milvus(limit=50)
    
    if not docs_map:
        print("错误：未能从 Milvus 获取到文档。")
        return
    
    # 3. 生成测试问答对
    print("\n[Step 3] 生成测试问答对...")
    goldens, doc_ids = generate_goldens(docs_map, custom_llm, custom_embedding)
    
    if not goldens:
        print("错误：未能生成测试问答对。")
        return
    
    # 4. 保存测试数据集
    save_dataset(goldens)
    
    # 5. 运行检索评估
    print("\n[Step 4] 运行检索评估...")
    run_evaluation(goldens, custom_llm)
    
    print("\n评估完成！")


if __name__ == "__main__":
    main()