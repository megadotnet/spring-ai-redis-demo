"""
RAG 评估服务
============
使用 DeepEval 进行 RAG 检索质量评估
"""

import json
import random
from pathlib import Path
from typing import List, Dict, Tuple, Optional

from deepeval.synthesizer import Synthesizer
from deepeval.dataset import EvaluationDataset, Golden
from deepeval.metrics import ContextualRecallMetric
from deepeval.test_case import LLMTestCase
from deepeval import evaluate

from ..config import EvaluationConfig
from ..models.llm import SiliconFlowLLM
from .spring_ai_service import SpringAIService, RetrievalResult


class Evaluator:
    """RAG 评估器类"""
    
    def __init__(self, config: EvaluationConfig, llm: SiliconFlowLLM, 
                 spring_ai_service: SpringAIService):
        self.config = config
        self.llm = llm
        self.spring_ai_service = spring_ai_service
    
    def load_cached_dataset(self) -> Optional[List[Golden]]:
        """尝试从缓存加载已生成的测试数据集"""
        cache_path = Path(self.config.dataset_cache_file)
        if cache_path.exists():
            print(f"发现缓存文件: {self.config.dataset_cache_file}，正在加载...")
            try:
                dataset = EvaluationDataset()
                dataset.pull(
                    self.config.dataset_cache_file.replace('.json', ''),
                    auto_convert_goldens_to_test_cases=False
                )
                if dataset.goldens:
                    print(f"从缓存加载了 {len(dataset.goldens)} 条测试用例。")
                    return dataset.goldens
            except Exception as e:
                print(f"缓存加载失败: {e}，将重新生成...")
        return None
    
    def generate_goldens(self, docs_map: Dict[str, str]) -> Tuple[List[Golden], List[str]]:
        """使用 DeepEval Synthesizer 生成测试问答对
        
        Args:
            docs_map: 文档 ID 到内容的映射
            
        Returns:
            Tuple[goldens, doc_ids]: 生成的测试用例和文档 ID 列表
        """
        # 尝试从缓存加载
        cached = self.load_cached_dataset()
        if cached:
            return cached, list(docs_map.keys())
        
        # 随机采样以减少处理量
        doc_items = list(docs_map.items())
        if len(doc_items) > self.config.num_contexts_to_sample:
            print(f"从 {len(doc_items)} 个文档中随机采样 {self.config.num_contexts_to_sample} 个...")
            doc_items = random.sample(doc_items, self.config.num_contexts_to_sample)
        
        doc_ids = [item[0] for item in doc_items]
        contexts = [[item[1]] for item in doc_items]
        
        print(f"正在使用 {len(contexts)} 个上下文生成测试问答对...")
        print(f"每个 context 生成 {self.config.max_goldens_per_context} 个 golden")
        
        synthesizer = Synthesizer(model=self.llm)
        
        goldens = synthesizer.generate_goldens_from_contexts(
            contexts=contexts,
            max_goldens_per_context=self.config.max_goldens_per_context,
            include_expected_output=True
        )
        
        final_goldens = goldens[:self.config.num_goldens_to_generate]
        print(f"生成完成！共获得 {len(final_goldens)} 条测试用例。")
        
        return final_goldens, doc_ids
    
    @staticmethod
    def evaluate_id_hit_rate(expected_doc_ids: List[str], 
                             retrieved_doc_ids: List[str]) -> float:
        """计算 ID 命中率
        
        Args:
            expected_doc_ids: 期望的文档 ID 列表
            retrieved_doc_ids: 实际检索到的文档 ID 列表
            
        Returns:
            float: 命中率 (0.0 ~ 1.0)
        """
        if not expected_doc_ids:
            return 0.0
        
        expected_set = set(expected_doc_ids)
        retrieved_set = set(retrieved_doc_ids)
        hits = len(expected_set & retrieved_set)
        
        return hits / len(expected_set)
    
    def run_evaluation(self, goldens: List[Golden]) -> Dict:
        """执行完整的 RAG 评估流程
        
        Args:
            goldens: 测试用例列表
            
        Returns:
            Dict: 评估报告
        """
        print("\n" + "=" * 50)
        print("开始 RAG 检索质量评估")
        print("=" * 50)
        
        metric = ContextualRecallMetric(
            threshold=self.config.context_recall_threshold,
            model=self.llm,
            include_reason=True
        )
        
        test_cases = []
        id_hit_rates = []
        
        for i, golden in enumerate(goldens):
            question = golden.input
            expected_output = golden.expected_output or ""
            
            print(f"\n[{i+1}/{len(goldens)}] 正在评估问题: {question[:50]}...")
            
            # 调用 Spring AI 检索接口
            retrieval_results = self.spring_ai_service.retrieve(
                question, 
                top_k=self.config.top_k
            )
            
            if not retrieval_results:
                print(f"  ⚠️ 检索结果为空，跳过此用例")
                continue
            
            retrieved_contexts = [r.content for r in retrieval_results]
            retrieved_doc_ids = [r.id for r in retrieval_results]
            
            id_hit_rate = self.evaluate_id_hit_rate([], retrieved_doc_ids)
            id_hit_rates.append(id_hit_rate)
            
            test_case = LLMTestCase(
                input=question,
                actual_output=" ".join(retrieved_contexts),
                expected_output=expected_output,
                retrieval_context=retrieved_contexts
            )
            test_cases.append(test_case)
        
        if not test_cases:
            print("没有有效的测试用例，评估终止。")
            return {}
        
        print(f"\n正在使用 ContextualRecallMetric 评估 {len(test_cases)} 个测试用例...")
        
        results = evaluate(
            test_cases=test_cases,
            metrics=[metric]
        )
        
        # 计算汇总结果
        # 计算汇总结果
        scores = []
        individual_details = []

        for i, result in enumerate(results):
            score = 0
            reason = None
            # result 是 TestResult 对象
            if hasattr(result, 'metrics') and result.metrics:
                score = result.metrics[0].score
                reason = result.metrics[0].reason
            
            scores.append(score)
            individual_details.append({
                "question": test_cases[i].input[:100],
                "score": score,
                "reason": reason
            })

        avg_score = sum(scores) / len(scores) if scores else 0
        avg_id_hit_rate = sum(id_hit_rates) / len(id_hit_rates) if id_hit_rates else 0
        
        report = {
            "total_test_cases": len(test_cases),
            "average_context_recall": avg_score,
            "average_id_hit_rate": avg_id_hit_rate,
            "individual_results": individual_details
        }
        
        # 打印和保存报告
        print("\n" + "=" * 50)
        print("====== RAG 评估报告 ======")
        print("=" * 50)
        print(f"总测试用例数: {len(test_cases)}")
        print(f"ContextRecall 平均分: {avg_score:.4f}")
        print(f"ID 命中率: {avg_id_hit_rate * 100:.2f}%")
        
        with open("evaluation_report.json", "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        
        print(f"详细结果已保存至: evaluation_report.json")
        
        return report
    
    @staticmethod
    def save_dataset(goldens: List[Golden], filename: str = "rag_test_dataset"):
        """保存测试数据集"""
        dataset = EvaluationDataset(goldens=goldens)
        # DeepEval API: save_as(file_type, directory, file_name)
        dataset.save_as(file_type='json', directory='.', file_name=filename)
        print(f"测试数据集已保存为: {filename}.json")
