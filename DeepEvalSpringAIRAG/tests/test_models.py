"""
模型模块单元测试
"""

import pytest
from unittest.mock import Mock, patch, MagicMock


class TestSiliconFlowLLM:
    """测试 LLM 模型"""
    
    def test_init(self):
        """测试初始化"""
        from src.models.llm import SiliconFlowLLM
        
        with patch("src.models.llm.OpenAI") as mock_openai:
            llm = SiliconFlowLLM(api_key="test-key")
            
            assert llm.model_name == "Qwen/Qwen2.5-7B-Instruct"
            mock_openai.assert_called_once_with(
                api_key="test-key",
                base_url="https://api.siliconflow.cn/v1"
            )
    
    def test_init_custom_model(self):
        """测试自定义模型名称"""
        from src.models.llm import SiliconFlowLLM
        
        with patch("src.models.llm.OpenAI"):
            llm = SiliconFlowLLM(
                api_key="test-key",
                model_name="custom/model"
            )
            assert llm.model_name == "custom/model"
    
    def test_generate_success(self):
        """测试生成成功"""
        from src.models.llm import SiliconFlowLLM
        
        with patch("src.models.llm.OpenAI") as mock_openai:
            # 构造 mock 响应
            mock_response = MagicMock()
            mock_response.choices = [MagicMock()]
            mock_response.choices[0].message.content = "Generated text"
            mock_openai.return_value.chat.completions.create.return_value = mock_response
            
            llm = SiliconFlowLLM(api_key="test-key")
            result = llm.generate("Test prompt")
            
            assert result == "Generated text"
    
    def test_generate_error(self):
        """测试生成失败返回空字符串"""
        from src.models.llm import SiliconFlowLLM
        
        with patch("src.models.llm.OpenAI") as mock_openai:
            mock_openai.return_value.chat.completions.create.side_effect = Exception("API Error")
            
            llm = SiliconFlowLLM(api_key="test-key")
            result = llm.generate("Test prompt")
            
            assert result == ""
    
    def test_get_model_name(self):
        """测试获取模型名称"""
        from src.models.llm import SiliconFlowLLM
        
        with patch("src.models.llm.OpenAI"):
            llm = SiliconFlowLLM(api_key="test-key", model_name="test/model")
            assert llm.get_model_name() == "test/model"


class TestSiliconFlowBGEEmbedding:
    """测试 Embedding 模型"""
    
    def test_init(self):
        """测试初始化"""
        from src.models.embedding import SiliconFlowBGEEmbedding
        
        with patch("src.models.embedding.OpenAI") as mock_openai:
            embedding = SiliconFlowBGEEmbedding(api_key="test-key")
            
            assert embedding.model_name == "BAAI/bge-large-en-v1.5"
            mock_openai.assert_called_once()
    
    def test_embed_text_success(self):
        """测试单文本 embedding 成功"""
        from src.models.embedding import SiliconFlowBGEEmbedding
        
        with patch("src.models.embedding.OpenAI") as mock_openai:
            mock_response = MagicMock()
            mock_response.data = [MagicMock()]
            mock_response.data[0].embedding = [0.1, 0.2, 0.3]
            mock_openai.return_value.embeddings.create.return_value = mock_response
            
            embedding = SiliconFlowBGEEmbedding(api_key="test-key")
            result = embedding.embed_text("Test text")
            
            assert result == [0.1, 0.2, 0.3]
    
    def test_embed_texts_success(self):
        """测试批量 embedding 成功"""
        from src.models.embedding import SiliconFlowBGEEmbedding
        
        with patch("src.models.embedding.OpenAI") as mock_openai:
            mock_response = MagicMock()
            mock_response.data = [
                MagicMock(embedding=[0.1, 0.2]),
                MagicMock(embedding=[0.3, 0.4])
            ]
            mock_openai.return_value.embeddings.create.return_value = mock_response
            
            embedding = SiliconFlowBGEEmbedding(api_key="test-key")
            result = embedding.embed_texts(["Text 1", "Text 2"])
            
            assert result == [[0.1, 0.2], [0.3, 0.4]]
    
    def test_embed_text_error(self):
        """测试 embedding 失败返回空列表"""
        from src.models.embedding import SiliconFlowBGEEmbedding
        
        with patch("src.models.embedding.OpenAI") as mock_openai:
            mock_openai.return_value.embeddings.create.side_effect = Exception("API Error")
            
            embedding = SiliconFlowBGEEmbedding(api_key="test-key")
            result = embedding.embed_text("Test")
            
            assert result == []
