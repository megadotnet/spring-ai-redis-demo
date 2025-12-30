"""
配置模块单元测试
"""

import os
import pytest
from unittest.mock import patch


class TestConfig:
    """测试配置加载"""
    
    def test_get_required_env_exists(self):
        """测试获取存在的环境变量"""
        from src.config import get_required_env
        
        with patch.dict(os.environ, {"TEST_KEY": "test_value"}):
            result = get_required_env("TEST_KEY")
            assert result == "test_value"
    
    def test_get_required_env_missing(self):
        """测试获取不存在的环境变量应抛出异常"""
        from src.config import get_required_env
        
        with patch.dict(os.environ, {}, clear=True):
            # 确保环境变量不存在
            os.environ.pop("NONEXISTENT_KEY", None)
            with pytest.raises(ValueError) as excinfo:
                get_required_env("NONEXISTENT_KEY")
            assert "NONEXISTENT_KEY" in str(excinfo.value)
    
    def test_siliconflow_config_defaults(self):
        """测试 SiliconFlowConfig 默认值"""
        from src.config import SiliconFlowConfig
        
        config = SiliconFlowConfig(api_key="test-key")
        assert config.api_key == "test-key"
        assert config.base_url == "https://api.siliconflow.cn/v1"
        assert config.llm_model == "Qwen/Qwen2.5-7B-Instruct"
    
    def test_milvus_config_defaults(self):
        """测试 MilvusConfig 默认值"""
        from src.config import MilvusConfig
        
        config = MilvusConfig()
        assert config.uri == "http://10.1.1.234:19530"
        assert config.collection_name == "paper_collection_vpdf"
    
    def test_spring_ai_config_retrieve_url(self):
        """测试 SpringAIConfig retrieve_url 属性"""
        from src.config import SpringAIConfig
        
        config = SpringAIConfig(base_url="http://localhost:9090")
        assert config.retrieve_url == "http://localhost:9090/retrieve"
    
    def test_evaluation_config_defaults(self):
        """测试 EvaluationConfig 默认值"""
        from src.config import EvaluationConfig
        
        config = EvaluationConfig()
        assert config.num_goldens_to_generate == 25
        assert config.top_k == 5
        assert config.context_recall_threshold == 0.7
