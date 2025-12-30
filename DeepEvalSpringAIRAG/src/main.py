"""
DeepEval RAG 评估脚本 - 主入口
=============================
功能：
1. 从 Milvus 扫描文档，使用 DeepEval Synthesizer 生成测试问答对
2. 调用 Spring AI 的检索接口获取 Top-K 结果
3. 使用 ContextualRecallMetric 评估检索质量
"""

from .config import get_config
from .models import SiliconFlowLLM, SiliconFlowBGEEmbedding
from .services import MilvusService, SpringAIService, Evaluator


def main():
    """主入口函数"""
    print("=" * 50)
    print("DeepEval RAG 评估脚本")
    print("=" * 50)
    
    # 1. 加载配置
    config = get_config()
    
    # 2. 初始化模型
    print(f"\n[Step 1] 初始化模型 ({config.siliconflow.llm_model})...")
    llm = SiliconFlowLLM(
        api_key=config.siliconflow.api_key,
        model_name=config.siliconflow.llm_model,
        base_url=config.siliconflow.base_url
    )
    embedding = SiliconFlowBGEEmbedding(
        api_key=config.siliconflow.api_key,
        model_name=config.siliconflow.embedding_model,
        base_url=config.siliconflow.base_url
    )
    
    # 3. 初始化服务
    milvus_service = MilvusService(config.milvus)
    spring_ai_service = SpringAIService(config.spring_ai)
    evaluator = Evaluator(config.evaluation, llm, spring_ai_service)
    
    # 4. 从 Milvus 获取文档
    print("\n[Step 2] 从 Milvus 获取文档...")
    docs_map = milvus_service.fetch_docs(limit=50)
    
    if not docs_map:
        print("错误：未能从 Milvus 获取到文档。")
        return
    
    # 5. 生成测试问答对
    print("\n[Step 3] 生成测试问答对...")
    goldens, doc_ids = evaluator.generate_goldens(docs_map)
    
    if not goldens:
        print("错误：未能生成测试问答对。")
        return
    
    # 6. 保存测试数据集
    Evaluator.save_dataset(goldens)
    
    # 7. 运行检索评估
    print("\n[Step 4] 运行检索评估...")
    evaluator.run_evaluation(goldens)
    
    # 8. 清理资源
    milvus_service.close()
    
    print("\n评估完成！")


if __name__ == "__main__":
    main()
