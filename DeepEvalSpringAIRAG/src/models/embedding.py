"""
硅基流动 Embedding 模型适配器
============================
适配 DeepEval 的 Embedding 接口，使用硅基流动 API
"""

from typing import List
from openai import OpenAI
from deepeval.models import DeepEvalBaseEmbeddingModel


class SiliconFlowBGEEmbedding(DeepEvalBaseEmbeddingModel):
    """使用硅基流动的 BAAI/bge-large-en-v1.5 模型"""
    
    def __init__(self, api_key: str, model_name: str = "BAAI/bge-large-en-v1.5",
                 base_url: str = "https://api.siliconflow.cn/v1"):
        self.model_name = model_name
        self.api_key = api_key
        self.client = OpenAI(
            api_key=api_key,
            base_url=base_url
        )
        super().__init__(model=self.model_name)

    def load_model(self):
        return self.client

    def embed_text(self, text: str) -> List[float]:
        """生成单个文本的 embedding"""
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
        """批量生成文本的 embeddings"""
        try:
            response = self.client.embeddings.create(
                model=self.model_name,
                input=texts
            )
            return [item.embedding for item in response.data]
        except Exception as e:
            print(f"Embedding batch error: {e}")
            return []

    async def a_embed_text(self, text: str) -> List[float]:
        return self.embed_text(text)

    async def a_embed_texts(self, texts: List[str]) -> List[List[float]]:
        return self.embed_texts(texts)

    def get_model_name(self) -> str:
        return self.model_name
