"""
硅基流动 LLM 模型适配器
======================
适配 DeepEval 的 LLM 接口，使用硅基流动 API
"""

from typing import List
from openai import OpenAI
from deepeval.models.base_model import DeepEvalBaseLLM


class SiliconFlowLLM(DeepEvalBaseLLM):
    """使用硅基流动 API 的 LLM - 默认使用 Qwen2.5 模型"""
    
    def __init__(self, api_key: str, model_name: str = "Qwen/Qwen2.5-7B-Instruct",
                 base_url: str = "https://api.siliconflow.cn/v1"):
        self.model_name = model_name
        self.client = OpenAI(
            api_key=api_key,
            base_url=base_url
        )

    def load_model(self):
        return self.client

    def generate(self, prompt: str, temperature: float = 0.7, max_tokens: int = 2048) -> str:
        """同步生成方法"""
        try:
            response = self.client.chat.completions.create(
                model=self.model_name,
                messages=[{"role": "user", "content": prompt}],
                temperature=temperature,
                max_tokens=max_tokens
            )
            return response.choices[0].message.content
        except Exception as e:
            print(f"LLM Generate error: {e}")
            return ""

    async def a_generate(self, prompt: str) -> str:
        """异步生成方法（调用同步实现）"""
        return self.generate(prompt)

    def get_model_name(self) -> str:
        return self.model_name
