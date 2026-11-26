"""
配置管理模块
============
集中管理所有配置项，支持环境变量和默认值
"""

import os
from dataclasses import dataclass
from typing import Optional


def get_required_env(key: str) -> str:
    """获取必需的环境变量，不存在则抛出异常"""
    value = os.getenv(key)
    if not value:
        raise ValueError(
            f"未找到环境变量 '{key}'。请在运行前设置，例如：\n"
            f"Linux/Mac: export {key}='your-value'\n"
            f"Windows: set {key}=your-value"
        )
    return value


@dataclass
class SiliconFlowConfig:
    """硅基流动 API 配置"""
    api_key: str
    base_url: str = "https://api.siliconflow.cn/v1"
    llm_model: str = "Qwen/Qwen2.5-7B-Instruct"
    embedding_model: str = "BAAI/bge-large-en-v1.5"


@dataclass
class MilvusConfig:
    """Milvus 向量数据库配置"""
    uri: str = "http://10.1.1.234:19530"
    token: str = "root:Milvus"
    collection_name: str = "paper_collection_vpdf"


@dataclass
class SpringAIConfig:
    """Spring AI 服务配置"""
    base_url: str = "http://localhost:8080"
    retrieve_endpoint: str = "/retrieve"
    
    @property
    def retrieve_url(self) -> str:
        return f"{self.base_url}{self.retrieve_endpoint}"


@dataclass
class EvaluationConfig:
    """评估配置"""
    num_goldens_to_generate: int = 25
    top_k: int = 5
    context_recall_threshold: float = 0.7
    num_contexts_to_sample: int = 25
    max_goldens_per_context: int = 1
    dataset_cache_file: str = "rag_test_dataset.json"


class Config:
    """全局配置类"""
    
    def __init__(self):
        self.siliconflow = SiliconFlowConfig(
            api_key=get_required_env("SILICONFLOW_KEY")
        )
        self.milvus = MilvusConfig()
        self.spring_ai = SpringAIConfig()
        self.evaluation = EvaluationConfig()
    
    @classmethod
    def from_env(cls) -> "Config":
        """从环境变量创建配置实例"""
        return cls()


# 单例配置实例（懒加载）
_config: Optional[Config] = None


def get_config() -> Config:
    """获取全局配置实例"""
    global _config
    if _config is None:
        _config = Config.from_env()
    return _config
