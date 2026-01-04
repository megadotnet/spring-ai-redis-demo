"""
服务模块单元测试
"""

import pytest
from unittest.mock import Mock, patch, MagicMock, mock_open
import requests


class TestMilvusService:
    """测试 Milvus 服务"""
    
    def test_fetch_docs_success(self):
        """测试成功获取文档"""
        from src.config import MilvusConfig
        from src.services.milvus_service import MilvusService
        
        with patch("src.services.milvus_service.MilvusClient") as mock_client:
            # 模拟查询结果
            mock_client.return_value.query.return_value = [
                {"doc_id": "doc1", "content": "Content 1"},
                {"doc_id": "doc2", "content": "Content 2"}
            ]
            
            config = MilvusConfig()
            service = MilvusService(config)
            result = service.fetch_docs(limit=10)
            
            assert result == {"doc1": "Content 1", "doc2": "Content 2"}
            assert result == {"doc1": "Content 1", "doc2": "Content 2"}
    
    def test_fetch_docs_empty(self):
        """测试空结果"""
        from src.config import MilvusConfig
        from src.services.milvus_service import MilvusService
        
        with patch("src.services.milvus_service.MilvusClient") as mock_client:
            mock_client.return_value.query.return_value = []
            
            config = MilvusConfig()
            service = MilvusService(config)
            result = service.fetch_docs()
            
            assert result == {}

    def test_client_lazy_loading(self):
        """测试 client 属性懒加载"""
        from src.config import MilvusConfig
        from src.services.milvus_service import MilvusService
        
        with patch("src.services.milvus_service.MilvusClient") as mock_client_cls:
            config = MilvusConfig()
            service = MilvusService(config)
            
            # 此时不应初始化
            mock_client_cls.assert_not_called()
            
            # 第一次调用
            client1 = service.client
            mock_client_cls.assert_called_once()
            
            # 第二次调用，不应再次初始化
            client2 = service.client
            assert client2 is client1
            mock_client_cls.assert_called_once()

    def test_close(self):
        """测试关闭连接"""
        from src.config import MilvusConfig
        from src.services.milvus_service import MilvusService
        
        with patch("src.services.milvus_service.MilvusClient") as mock_client_cls:
            service = MilvusService(MilvusConfig())
            
            # 初始化 client
            client = service.client
            
            # 关闭
            service.close()
            client.close.assert_called_once()
            assert service._client is None
            
            # 再次关闭不应报错
            service.close()


class TestSpringAIService:
    """测试 Spring AI 服务"""
    
    def test_retrieve_success(self):
        """测试成功检索"""
        from src.config import SpringAIConfig
        from src.services.spring_ai_service import SpringAIService
        
        with patch("src.services.spring_ai_service.requests.post") as mock_post:
            mock_response = Mock()
            mock_response.json.return_value = [
                {"id": "1", "content": "Result 1", "score": 0.9, "metadata": {}},
                {"id": "2", "content": "Result 2", "score": 0.8, "metadata": {}}
            ]
            mock_response.raise_for_status = Mock()
            mock_post.return_value = mock_response
            
            config = SpringAIConfig()
            service = SpringAIService(config)
            results = service.retrieve("test query", top_k=2)
            
            assert len(results) == 2
            assert results[0].id == "1"
            assert results[0].content == "Result 1"
            assert results[0].score == 0.9
    
    def test_retrieve_failure(self):
        """测试检索失败返回空列表"""
        from src.config import SpringAIConfig
        from src.services.spring_ai_service import SpringAIService
        
        with patch("src.services.spring_ai_service.requests.post") as mock_post:
            mock_post.side_effect = requests.exceptions.RequestException("Connection error")
            
            config = SpringAIConfig()
            service = SpringAIService(config)
            results = service.retrieve("test query")
            
            assert results == []
    
    def test_health_check_success(self):
        """测试健康检查成功"""
        from src.config import SpringAIConfig
        from src.services.spring_ai_service import SpringAIService
        
        with patch("src.services.spring_ai_service.requests.get") as mock_get:
            mock_get.return_value.status_code = 200
            
            config = SpringAIConfig()
            service = SpringAIService(config)
            
            assert service.health_check() is True
    
    def test_health_check_failure(self):
        """测试健康检查失败"""
        from src.config import SpringAIConfig
        from src.services.spring_ai_service import SpringAIService
        
        with patch("src.services.spring_ai_service.requests.get") as mock_get:
            mock_get.side_effect = Exception("Connection refused")
            
            config = SpringAIConfig()
            service = SpringAIService(config)
            
            assert service.health_check() is False


class TestEvaluator:
    """测试评估器"""
    
    def test_evaluate_id_hit_rate_full_match(self):
        """测试 ID 命中率 - 完全匹配"""
        from src.services.evaluator import Evaluator
        
        expected = ["doc1", "doc2", "doc3"]
        retrieved = ["doc1", "doc2", "doc3", "doc4"]
        
        rate = Evaluator.evaluate_id_hit_rate(expected, retrieved)
        assert rate == 1.0
    
    def test_evaluate_id_hit_rate_partial_match(self):
        """测试 ID 命中率 - 部分匹配"""
        from src.services.evaluator import Evaluator
        
        expected = ["doc1", "doc2", "doc3", "doc4"]
        retrieved = ["doc1", "doc3"]
        
        rate = Evaluator.evaluate_id_hit_rate(expected, retrieved)
        assert rate == 0.5
    
    def test_evaluate_id_hit_rate_no_match(self):
        """测试 ID 命中率 - 无匹配"""
        from src.services.evaluator import Evaluator
        
        expected = ["doc1", "doc2"]
        retrieved = ["doc3", "doc4"]
        
        rate = Evaluator.evaluate_id_hit_rate(expected, retrieved)
        assert rate == 0.0
    
    def test_evaluate_id_hit_rate_empty_expected(self):
        """测试 ID 命中率 - 空期望列表"""
        from src.services.evaluator import Evaluator
        
        expected = []
        retrieved = ["doc1", "doc2"]
        
        rate = Evaluator.evaluate_id_hit_rate(expected, retrieved)
        assert rate == 0.0

    def test_load_cached_dataset_success(self):
        """测试成功加载缓存数据集"""
        from src.services.evaluator import Evaluator
        from deepeval.dataset import EvaluationDataset
        
        config = Mock()
        config.dataset_cache_file = "test_cache.json"
        evaluator = Evaluator(config, Mock(), Mock())
        
        with patch("src.services.evaluator.Path.exists", return_value=True), \
             patch("src.services.evaluator.EvaluationDataset") as mock_dataset_cls:
            
            mock_dataset = Mock()
            # 模拟 goldens 属性
            mock_dataset.goldens = ["golden1", "golden2"]
            mock_dataset_cls.return_value = mock_dataset
            
            result = evaluator.load_cached_dataset()
            
            assert result == ["golden1", "golden2"]
            mock_dataset.pull.assert_called_with("test_cache", auto_convert_goldens_to_test_cases=False)

    def test_generate_goldens_cache_hit(self):
        """测试生成 Goldens - 缓存命中"""
        from src.services.evaluator import Evaluator
        
        evaluator = Evaluator(Mock(), Mock(), Mock())
        
        with patch.object(evaluator, 'load_cached_dataset', return_value=["golden1"]):
            goldens, doc_ids = evaluator.generate_goldens({"doc1": "content"})
            
            assert goldens == ["golden1"]
            assert doc_ids == ["doc1"]

    def test_generate_goldens_new(self):
        """测试生成新的 Goldens"""
        from src.services.evaluator import Evaluator
        
        config = Mock()
        config.num_contexts_to_sample = 2
        config.max_goldens_per_context = 1
        config.num_goldens_to_generate = 2
        
        evaluator = Evaluator(config, Mock(), Mock())
        
        docs_map = {"d1": "c1", "d2": "c2"}
        
        with patch.object(evaluator, 'load_cached_dataset', return_value=None), \
             patch("src.services.evaluator.Synthesizer") as mock_synth_cls:
             
            mock_synth = Mock()
            mock_synth.generate_goldens_from_contexts.return_value = ["g1", "g2", "g3"]
            mock_synth_cls.return_value = mock_synth
            
            goldens, doc_ids = evaluator.generate_goldens(docs_map)
            
            assert len(goldens) == 2  # truncated by num_goldens_to_generate
            assert set(doc_ids) == {"d1", "d2"}

    def test_run_evaluation_success(self):
        """测试运行评估成功"""
        from src.services.evaluator import Evaluator
        from deepeval.dataset import Golden
        from src.services.spring_ai_service import RetrievalResult
        
        # Mock Config
        config = Mock()
        config.top_k = 3
        config.context_recall_threshold = 0.5
        
        # Mock Dependencies
        llm = Mock()
        spring_ai_service = Mock()
        
        # Mock Retrieval Results
        spring_ai_service.retrieve.return_value = [
            RetrievalResult(id="d1", content="c1", score=0.9, metadata={})
        ]
        
        evaluator = Evaluator(config, llm, spring_ai_service)
        goldens = [Golden(input="q1", expected_output="a1")]
        
        goldens = [Golden(input="q1", expected_output="a1")]
        
        goldens = [Golden(input="q1", expected_output="a1")]
        
        with patch("src.services.evaluator.ContextualRecallMetric") as mock_metric_cls, \
             patch("src.services.evaluator.evaluate") as mock_evaluate, \
             patch("src.services.evaluator.LLMTestCase") as mock_test_case_cls, \
             patch("builtins.open", new_callable=mock_open) as mock_file_open, \
             patch("json.dump"):
             
             # Mock Metric Score
             mock_metric_instance = Mock()
             mock_metric_cls.return_value = mock_metric_instance
             
             # Mock LLMTestCase to be a MagicMock that we can attach attributes to
             mock_test_case_instance = MagicMock()
             mock_test_case_instance.input = "q1"
             mock_test_case_cls.return_value = mock_test_case_instance
             
             # Mock Evaluate side effect
             def side_effect_evaluate(test_cases, metrics):
                 for tc in test_cases:
                     tc.metrics_data = [Mock(score=0.8, reason="Good")]
                 return "mock_results"
            
             mock_evaluate.side_effect = side_effect_evaluate
             
             report = evaluator.run_evaluation(goldens)
             
             assert report['total_test_cases'] == 1
             assert report['average_context_recall'] == 0.8
             assert report['average_id_hit_rate'] == 0.0
    
    def test_save_dataset_calls_correct_api(self):
        """测试 save_dataset 调用正确的 DeepEval API 参数"""
        from src.services.evaluator import Evaluator
        
        with patch("src.services.evaluator.EvaluationDataset") as mock_dataset_class:
            mock_dataset = MagicMock()
            mock_dataset_class.return_value = mock_dataset
            
            # 创建 mock goldens
            mock_goldens = [MagicMock(), MagicMock()]
            
            Evaluator.save_dataset(mock_goldens, "test_dataset")
            
            # 验证 EvaluationDataset 被正确创建
            mock_dataset_class.assert_called_once_with(goldens=mock_goldens)
            
            # 验证 save_as 被正确调用，包含所有必需参数
            mock_dataset.save_as.assert_called_once_with(
                file_type='json',
                directory='.',
                file_name='test_dataset'
            )
    
    def test_save_dataset_default_filename(self):
        """测试 save_dataset 使用默认文件名"""
        from src.services.evaluator import Evaluator
        
        with patch("src.services.evaluator.EvaluationDataset") as mock_dataset_class:
            mock_dataset = MagicMock()
            mock_dataset_class.return_value = mock_dataset
            
            Evaluator.save_dataset([])
            
            # 验证使用默认文件名
            mock_dataset.save_as.assert_called_once_with(
                file_type='json',
                directory='.',
                file_name='rag_test_dataset'
            )
