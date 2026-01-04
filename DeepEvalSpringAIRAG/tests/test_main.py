"""
主程序单元测试
"""
import pytest
from unittest.mock import patch, Mock

class TestMain:
    """测试主入口"""
    
    @patch("src.main.get_config")
    @patch("src.main.SiliconFlowLLM")
    @patch("src.main.SiliconFlowBGEEmbedding")
    @patch("src.main.MilvusService")
    @patch("src.main.SpringAIService")
    @patch("src.main.Evaluator")
    def test_main_success(self, mock_evaluator, mock_spring, mock_milvus, 
                          mock_embed, mock_llm, mock_get_config):
        """测试主流程成功执行"""
        from src.main import main
        
        # Setup Mocks
        config = Mock()
        mock_get_config.return_value = config
        
        milvus_instance = mock_milvus.return_value
        milvus_instance.fetch_docs.return_value = {"d1": "c1"}
        
        eval_instance = mock_evaluator.return_value
        eval_instance.generate_goldens.return_value = (["g1"], ["d1"])
        
        # Run main
        main()
        
        # Verify calls
        milvus_instance.fetch_docs.assert_called_once()
        eval_instance.generate_goldens.assert_called_once()
        # save_dataset is a static method, called on the class
        mock_evaluator.save_dataset.assert_called_once()
        eval_instance.run_evaluation.assert_called_once()
        milvus_instance.close.assert_called_once()

    @patch("src.main.get_config")
    @patch("src.main.MilvusService")
    @patch("src.main.SiliconFlowLLM") # Needed because main instantiates these
    @patch("src.main.SiliconFlowBGEEmbedding")
    @patch("src.main.SpringAIService")
    @patch("src.main.Evaluator")
    def test_main_no_docs(self, mock_eval, mock_spring, mock_embed, mock_llm, mock_milvus, mock_get_config):
        """测试无文档时提前退出"""
        from src.main import main
        
        mock_milvus.return_value.fetch_docs.return_value = {}
        
        main()
        
        mock_eval.return_value.generate_goldens.assert_not_called()

    @patch("src.main.get_config")
    @patch("src.main.MilvusService")
    @patch("src.main.SiliconFlowLLM")
    @patch("src.main.SiliconFlowBGEEmbedding")
    @patch("src.main.SpringAIService")
    @patch("src.main.Evaluator")
    def test_main_no_goldens(self, mock_eval, mock_spring, mock_embed, mock_llm, mock_milvus, mock_get_config):
        """测试无测试用例时提前退出"""
        from src.main import main
        
        mock_milvus.return_value.fetch_docs.return_value = {"d1": "c1"}
        mock_eval.return_value.generate_goldens.return_value = ([], [])
        
        main()
        
        mock_eval.return_value.run_evaluation.assert_not_called()
