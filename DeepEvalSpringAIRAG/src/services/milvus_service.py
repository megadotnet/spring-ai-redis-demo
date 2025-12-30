"""
Milvus 向量数据库服务
====================
提供从 Milvus 获取文档的功能
"""

from typing import Dict
from pymilvus import MilvusClient

from ..config import MilvusConfig


class MilvusService:
    """Milvus 文档服务类"""
    
    def __init__(self, config: MilvusConfig):
        self.config = config
        self._client = None
    
    @property
    def client(self) -> MilvusClient:
        """懒加载 Milvus 客户端"""
        if self._client is None:
            print(f"正在连接 Milvus ({self.config.uri})...")
            self._client = MilvusClient(
                uri=self.config.uri,
                token=self.config.token
            )
        return self._client
    
    def fetch_docs(self, limit: int = 50) -> Dict[str, str]:
        """从 Milvus 读取文档
        
        Args:
            limit: 最大返回文档数量
            
        Returns:
            Dict[doc_id, content]: 文档 ID 到内容的映射
        """
        print(f"正在查询集合: {self.config.collection_name}...")
        
        res = self.client.query(
            collection_name=self.config.collection_name,
            filter='doc_id != ""',
            output_fields=["content", "doc_id"],
            limit=limit
        )
        
        docs_map = {str(r['doc_id']): r['content'] for r in res}
        print(f"成功获取 {len(docs_map)} 篇文档片段。")
        return docs_map
    
    def close(self):
        """关闭连接"""
        if self._client:
            self._client.close()
            self._client = None
