"""
服务模块单元测试
"""

import pytest
from unittest.mock import Mock, patch, MagicMock
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
