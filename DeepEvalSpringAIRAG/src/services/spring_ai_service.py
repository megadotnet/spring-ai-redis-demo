"""
Spring AI 检索服务
=================
调用 Spring AI 的检索接口获取 Top-K 结果
"""

from typing import List, Dict, Any, Optional
from dataclasses import dataclass
import requests

from ..config import SpringAIConfig


@dataclass
class RetrievalResult:
    """检索结果数据类，与 Spring AI 返回结构对应"""
    id: str
    content: str
    score: Optional[float]
    metadata: Dict[str, Any]


class SpringAIService:
    """Spring AI 检索服务类"""
    
    def __init__(self, config: SpringAIConfig):
        self.config = config
    
    def retrieve(self, query: str, top_k: int = 5, timeout: int = 30) -> List[RetrievalResult]:
        """调用 Spring AI 检索接口获取 Top-K 结果
        
        Args:
            query: 查询文本
            top_k: 返回结果数量
            timeout: 请求超时时间（秒）
            
        Returns:
            List[RetrievalResult]: 检索结果列表
        """
        try:
            response = requests.post(
                self.config.retrieve_url,
                json={"query": query, "topK": str(top_k)},
                headers={"Content-Type": "application/json"},
                timeout=timeout
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
    
    def health_check(self) -> bool:
        """检查 Spring AI 服务是否可用"""
        try:
            response = requests.get(
                f"{self.config.base_url}/actuator/health",
                timeout=5
            )
            return response.status_code == 200
        except:
            return False
