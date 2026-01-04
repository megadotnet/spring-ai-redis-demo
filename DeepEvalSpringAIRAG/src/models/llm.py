"""
硅基流动 LLM 模型适配器
======================
适配 DeepEval 的 LLM 接口，使用硅基流动 API
"""

from typing import List
from openai import OpenAI, AsyncOpenAI
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
        self.async_client = AsyncOpenAI(
            api_key=api_key,
            base_url=base_url
        )

    def load_model(self):
        return self.client

    def _process_response(self, content: str, schema: dict = None):
        """处理 LLM 响应内容"""
        import json
        
        # 如果需要结构化输出 (schema 存在)
        if schema:
            # 尝试清理 markdown 代码块标记
            content = content.replace("```json", "").replace("```", "").strip()
            try:
                # 解析 JSON
                json_data = json.loads(content)
                
                # 尝试直接转换
                try:
                    return schema(**json_data)
                except Exception as validation_error:
                    # 尝试 heuristic 修复: 如果 Schema 只有一个字段，尝试映射
                    fields = getattr(schema, 'model_fields', getattr(schema, '__fields__', {}))
                    if len(fields) == 1:
                        field_name = next(iter(fields))
                        # 策略 1: 如果 JSON 只有一个键值对，取值赋给该字段
                        if isinstance(json_data, dict) and len(json_data) == 1:
                            value = next(iter(json_data.values()))
                            print(f"[WARN] Schema mismatch. Mapping value from {list(json_data.keys())[0]} to {field_name}")
                            
                            try:
                                return schema(**{field_name: value})
                            except Exception:
                                # 如果直接映射失败 (例如类型不匹配，期望 str 但得到 int/list/dict)
                                # 尝试强制转换为字符串
                                print(f"[WARN] Type mismatch for field {field_name}. Value: {value} (type {type(value)}). Converting to string.")
                                return schema(**{field_name: str(value)})
                        
                        # Fallback: 如果是一对一字段，且上述尝试失败或不适用 (例如空字典)，
                        # 尝试将原始 content 赋给该字段
                        print(f"[WARN] JSON structure mismatch. Fallback: Mapping raw content to field {field_name}. Content: {content[:50]}...")
                        return schema(**{field_name: content})
                    
                    # 如果修复失败，打印详细信息并抛出
                    print(f"[ERROR] JSON Validation failed. Schema: {fields.keys()}, Data: {json_data}")
                    raise validation_error

            except json.JSONDecodeError as e:
                print(f"JSON Parse Error: {e}, Content: {content}")
                raise ValueError(f"Failed to parse JSON response: {content}") from e
        
        return content

    def generate(self, prompt: str, schema: dict = None, **kwargs) -> str:
        """同步生成方法"""
        
        temperature = kwargs.get('temperature', 0.7)
        max_tokens = kwargs.get('max_tokens', 2048)
        
        # 如果提供了 schema，尝试强制使用 JSON 模式
        extra_body = {}
        if schema:
            extra_body["response_format"] = {"type": "json_object"}

        try:
            response = self.client.chat.completions.create(
                model=self.model_name,
                messages=[{"role": "user", "content": prompt}],
                temperature=temperature,
                max_tokens=max_tokens,
                **extra_body
            )
            content = response.choices[0].message.content
            return self._process_response(content, schema)
            
        except Exception as e:
            print(f"LLM Generate error: {e}")
            if schema:
                raise e
            return ""

    async def a_generate(self, prompt: str, schema: dict = None, **kwargs) -> str:
        """异步生成方法"""
        
        temperature = kwargs.get('temperature', 0.7)
        max_tokens = kwargs.get('max_tokens', 2048)
        
        # 如果提供了 schema，尝试强制使用 JSON 模式
        extra_body = {}
        if schema:
            extra_body["response_format"] = {"type": "json_object"}

        try:
            response = await self.async_client.chat.completions.create(
                model=self.model_name,
                messages=[{"role": "user", "content": prompt}],
                temperature=temperature,
                max_tokens=max_tokens,
                **extra_body
            )
            content = response.choices[0].message.content
            return self._process_response(content, schema)
            
        except Exception as e:
            print(f"LLM Async Generate error: {e}")
            if schema:
                raise e
            return ""

    def get_model_name(self) -> str:
        return self.model_name
