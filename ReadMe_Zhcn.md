# Spring AI Redis Demo 技术架构与使用指南

[English](README.md) | 简体中文
[![zread](https://img.shields.io/badge/Ask_Zread-_.svg?style=flat&color=00b0aa&labelColor=000000&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIHZpZXdCb3g9IjAgMCAxNiAxNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTQuOTYxNTYgMS42MDAxSDIuMjQxNTZDMS44ODgxIDEuNjAwMSAxLjYwMTU2IDEuODg2NjQgMS42MDE1NiAyLjI0MDFWNC45NjAxQzEuNjAxNTYgNS4zMTM1NiAxLjg4ODEgNS42MDAxIDIuMjQxNTYgNS42MDAxSDQuOTYxNTZDNS4zMTUwMiA1LjYwMDEgNS42MDE1NiA1LjMxMzU2IDUuNjAxNTYgNC45NjAxVjIuMjQwMUM1LjYwMTU2IDEuODg2NjQgNS4zMTUwMiAxLjYwMDEgNC45NjE1NiAxLjYwMDFaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00Ljk2MTU2IDEwLjM5OTlIMi4yNDE1NkMxLjg4ODEgMTAuMzk5OSAxLjYwMTU2IDEwLjY4NjQgMS42MDE1NiAxMS4wMzk5VjEzLjc1OTlDMS42MDE1NiAxNC4xMTM0IDEuODg4MSAxNC4zOTk5IDIuMjQxNTYgMTQuMzk5OUg0Ljk2MTU2QzUuMzE1MDIgMTQuMzk5OSA1LjYwMTU2IDE0LjExMzQgNS42MDE1NiAxMy43NTk5VjExLjAzOTlDNS42MDE1NiAxMC42ODY0IDUuMzE1MDIgMTAuMzk5OSA0Ljk2MTU2IDEwLjM5OTlaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik0xMy43NTg0IDEuNjAwMUgxMS4wMzg0QzEwLjY4NSAxLjYwMDEgMTAuMzk4NCAxLjg4NjY0IDEwLjM5ODQgMi4yNDAxVjQuOTYwMUMxMC4zOTg0IDUuMzEzNTYgMTAuNjg1IDUuNjAwMSAxMS4wMzg0IDUuNjAwMUgxMy43NTg0QzE0LjExMTkgNS42MDAxIDE0LjM5ODQgNS4zMTM1NiAxNC4zOTg0IDQuOTYwMVYyLjI0MDFDMTQuMzk4NCAxLjg4NjY0IDE0LjExMTkgMS42MDAxIDEzLjc1ODQgMS42MDAxWiIgZmlsbD0iI2ZmZiIvPgo8cGF0aCBkPSJNNCAxMkwxMiA0TDQgMTJaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00IDEyTDEyIDQiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxLjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPgo8L3N2Zz4K&logoColor=ffffff)](https://zread.ai/megadotnet/spring-ai-redis-demo)

本项目是一个基于 Spring Boot 4.2.0 和 Spring AI 2.1.0 的检索增强生成（RAG）演示应用，结合了 Milvus 向量数据库和大型语言模型（LLM），实现面向领域知识文档的智能问答。

检索为混合模式——Lucene BM25 与 Milvus 向量检索并行召回，经 RRF 融合，再可选调用外部 cross-encoder 精排。在此之上，应用同时提供**两条独立的问答通道**：单次检索的 *Native* 链路，以及带查询规划、多轮检索与引用校验的 *Agentic* 有界运行时链路。

完整的架构解读（数据流、数据结构、存储布局、前后端契约）见 [Architecture.md](Architecture.md)。

---

## 🛠️ 技术栈清单

以下梳理了本项目各核心模块采用的具体技术选型、版本及核心作用。

### 1. 🎨 前端框架 (Frontend)

| 技术/组件 | 版本号 | 核心作用说明 |
| --- | --- | --- |
| **React** | `18.2.0` | 核心视图层库，采用函数式与 Class Component 架构构建用户界面。 |
| **React DOM** | `18.2.0` | 将 React 虚拟 DOM 渲染到 Web 端实际 DOM 的渲染器。 |
| **Bootstrap** | `5.3.2` | 提供响应式布局、预设样式与基础 CSS 框架。 |
| **React Bootstrap** | `2.9.2` | 将 Bootstrap 组件封装为 React 组件，方便基于状态驱动 UI 渲染。 |

### 2. ⚙️ 后端语言及运行环境 (Backend)

| 技术/组件 | 版本号 | 核心作用说明 |
| --- | --- | --- |
| **Java** | `17` | 后端核心开发语言，提供强大的生态支持和性能保障。 |
| **Spring Boot** | `4.2.0` | 应用脚手架，实现自动装配、快速构建生产级 RESTful API 服务。 |
| **Spring AI** | `2.1.0` | 统一抽象 LLM 和向量数据库交互，支持 Ollama、OpenAI 及 Milvus。 |
| **Apache Lucene** | `9.11.1` | 强大的文本搜索引擎库，用于实现本地化高性能的 BM25 关键词检索。 |
| **Apache POI** | `5.2.3` | 办公文档解析库（`poi` 及 `poi-ooxml`），辅助解析导入的结构化文档。 |

### 3. 🗄️ 数据库系统与基础设施 (Infrastructure)

| 技术/组件 | 版本号 | 核心作用说明 |
| --- | --- | --- |
| **Milvus** | 客户端 SDK `2.6.21` | 高性能向量数据库，存储高维嵌入向量（Embeddings）并实现快速的 Top-K 语义相似度搜索。`milvus-sdk-java` 由 `spring-ai-starter-vector-store-milvus` 传递引入；`docker-compose.yml` 部署的是 `milvusdb/milvus:v2.6.7` 单机版（etcd + MinIO + standalone），Testcontainers 集成测试也用同一镜像。 |
| **Redis** | Redis Stack Server | 用作高性能 KV 缓存，并借助 Spring Data Redis 持久化 BM25 全文索引，支持应用重启后的索引恢复。 |
| **Ollama** | 最新稳定版 | 本地运行的 LLM 引擎，用于执行 `qwen2.5:0.5b` 等语言模型及 `bge-m3:latest` 等嵌入模型。 |

### 4. 🧰 工具链与构建部署 (Toolchain)

| 技术/组件 | 版本号 | 核心作用说明 |
| --- | --- | --- |
| **Maven** | `3.6.3+` | 后端项目依赖管理及构建工具。 |
| **Node.js & npm** | Node `v18.16.0`+, npm `9.5.1`+ | 前端运行环境及包管理工具。 |
| **maven-resources-plugin** | Maven 默认 | 在 `process-classes` 阶段将已构建好的 `frontend/build` 拷入 `target/classes/static`，使 React SPA 与应用共用同一个 jar。**注意**：`pom.xml` 只声明了 `frontend-maven-plugin.version` 属性，并**没有**对应的 `<plugin>` 块，因此 Maven 不会下载 Node、也不会执行 `npm run build`，前端必须单独构建。 |
| **Docker & Compose** | `20+` | 容量化技术，统一编排 Redis、Milvus、应用本身等组件的部署环境，解决依赖冲突问题。 |
| **JaCoCo** | `0.8.16` | 代码覆盖率工具，在测试阶段生成统计报告，保障代码质量。 |
| **Testcontainers** | `1.21.4` | 集成测试用的容器运行时（Milvus 容器测试）。Spring Boot 4.2.0 不再统一管理该版本，已在 `pom.xml` 中显式钉住。 |

---

## 🤖 Chat、Embedding 与 Rerank 模型配置

项目将对话生成、文本嵌入和结果重排分为独立的模型角色。三者可以分别使用本地或远程 API。

| 角色 | 当前默认值 | API 地址 | 主要配置项 |
| --- | --- | --- | --- |
| Chat Model | 硅基流动 `Qwen/Qwen3-8B` | 远程：`https://api.siliconflow.cn` | `chat.provider=openai`、`spring.ai.openai.*` |
| Embedding Model | Ollama `bge-m3:latest` | 本地 Ollama：`http://127.0.0.1:11434` | `spring.ai.ollama.base-url`、`spring.ai.ollama.embedding.options.model` |
| Rerank Model | 硅基流动 `BAAI/bge-reranker-v2-m3` | 远程：`https://api.siliconflow.cn/v1/rerank` | `rerank.enabled=true`、`rerank.model` |

所有模型配置位于 [`src/main/resources/application.properties`](src/main/resources/application.properties)，Java 配置和调用实现位于：

* [`ChatModelConfiguration.java`](src/main/java/com/redis/demo/spring/ai/config/ChatModelConfiguration.java)
* [`RagConfiguration.java`](src/main/java/com/redis/demo/spring/ai/RagConfiguration.java)
* [`RerankService.java`](src/main/java/com/redis/demo/spring/ai/service/RerankService.java)

### 1. Chat Model

`chat.provider` 属性用于切换 Chat Model 实现：

```properties
# 可选值：ollama 或 openai
chat.provider=openai
```

* `chat.provider=ollama`：使用本地 Ollama Chat API，由 `spring.ai.ollama.chat.options.model` 和 `spring.ai.ollama.base-url` 配置。
* `chat.provider=openai`：使用 OpenAI 兼容的 Chat Completions API，可连接官方 OpenAI 或硅基流动等兼容服务。

当前远程硅基流动配置：

```properties
spring.ai.openai.api-key=${SILICONFLOW_KEY:}
spring.ai.openai.chat.model=Qwen/Qwen3-8B
spring.ai.openai.chat.options.model=Qwen/Qwen3-8B
spring.ai.openai.base-url=https://api.siliconflow.cn
spring.ai.openai.chat.options.temperature=0.7
```

启动前设置 API Key：

```bash
# macOS / Linux
export SILICONFLOW_KEY=你的硅基流动API_KEY

# Windows 命令提示符
set SILICONFLOW_KEY=你的硅基流动API_KEY
```

切换为本地 Ollama 对话模型：

```properties
chat.provider=ollama
spring.ai.ollama.base-url=http://127.0.0.1:11434
spring.ai.ollama.chat.options.model=qwen2.5:0.5b
spring.ai.ollama.connection-timeout=60s
spring.ai.ollama.read-timeout=60s
```

先下载并启动本地模型：

```bash
ollama pull qwen2.5:0.5b
ollama serve
```

### 2. Embedding Model

Embedding Model 当前不受 `chat.provider` 影响，始终使用 Ollama：

```properties
spring.ai.ollama.base-url=http://127.0.0.1:11434
spring.ai.ollama.embedding.options.model=bge-m3:latest
spring.ai.ollama.embedding.connect-timeout=10s
spring.ai.ollama.embedding.read-timeout=300s
```

文档入库和用户查询向量化都会调用 `127.0.0.1:11434` 上的 Ollama。若 Ollama 部署在 Docker 容器或其他主机，请修改 `spring.ai.ollama.base-url`。

从 Spring Boot 4.2 起，`OllamaApi` 内部的默认请求工厂变为 Reactor Netty，带来约 10 秒的有效读超时，因此上述 embedding 超时需显式设置，而不能依赖 `spring.ai.ollama.read-timeout`。

```bash
ollama pull bge-m3:latest
ollama serve
```

向量维度必须与 Embedding Model 输出维度一致。当前 Milvus 配置使用 1024 维：

```properties
spring.ai.vectorstore.milvus.embeddingDimension=1024
```

### 3. Rerank Model

精排默认只有显式启用时才生效：

```properties
rerank.enabled=true
rerank.model=BAAI/bge-reranker-v2-m3
rerank.topN=5
```

Rerank 请求使用 `SILICONFLOW_KEY` 调用远程接口 `https://api.siliconflow.cn/v1/rerank`。如果精排 API 调用失败，`RerankService` 会降级为原始候选顺序，并返回配置的 `topN` 文档。

API Key 的读取优先级：

1. 环境变量 `SILICONFLOW_KEY`
2. 配置项 `siliconflow.api-key`
3. 未配置时记录警告并返回空 Key

### DeepSeek-OCR

PDF OCR 是独立的远程文档识别能力，不属于 Chat、Embedding 或 Rerank 模型角色。它使用硅基流动的 DeepSeek-OCR API：

```properties
deepseek-ocr.enabled=true
deepseek-ocr.model=deepseek-ai/DeepSeek-OCR
```

同样使用 `SILICONFLOW_KEY`。PDF 页面会在本地渲染为 JPEG 图片并转成 Base64，发送到远程 API 识别为 Markdown，再进行文档切块。

---

## 🔀 两条问答通道

两条通道共用同一套检索栈、同一份会话存储与同一个 React 页面，区别只在控制流。

| | Native 通道 | Agentic 通道 |
| --- | --- | --- |
| 端点 | `POST /chat/{chatId}` | `POST /api/agent/chat` |
| 服务 | [`RagService.java`](src/main/java/com/redis/demo/spring/ai/service/RagService.java) | [`AgentRuntimeService.java`](src/main/java/com/redis/demo/spring/ai/agent/AgentRuntimeService.java) |
| 执行模型 | 异步（`CompletableFuture`） | 同步 |
| 检索轮数 | 严格 1 次 | 最多 `MAX_TOOL_CALLS = 3` 次 |
| 查询规划 | 无，直接用原始问题检索 | `QueryPlanner` 将复合问题拆为子查询 |
| 请求体 | `{ "prompt": "..." }` | `{ "sessionId", "question", "topK"? }` |
| 响应体 | `{ "message": "..." }` | `answer` + `evidence[]` + `plan` + `grounded` + `traceId`；`api.js` 会把 `answer` 镜像到 `message`，使 `ChatWindow` 无需修改即可兼容两条通道 |
| 引用校验 | 无 | `GroundingChecker` 要求至少一个 `[证据 N]` 标记，且每个引用的 ID 必须能在返回的证据中回查 |
| 前端入口 | `#/native` | `#/agentic`（额外渲染可折叠证据面板） |

Agentic 通道是**有界**运行时，不是开放式工具循环：所有上限都是 `AgentRuntimeService` 里的编译期常量（`MAX_TOOL_CALLS = 3`、`DEFAULT_TOP_K = 5`、`MAX_TOP_K = 20`、`MAX_CONTEXT_CHARS = 12_000`），控制流全部留在 Java，LLM 只负责生成文本，从不决定下一个要调用哪个工具。

行为通过接口选配，任一依赖缺失时通道可自动降级而不需改代码：

| 接口 | 实现 | 选择依据 |
| --- | --- | --- |
| `RetrievalTool` | `HybridRetrievalTool` / `VectorOnlyRetrievalTool` | `hybrid.search.enabled` |
| `QueryPlanner` | `RuleBasedQueryPlanner`（正则 + 关键词拆分，不调 LLM） | 目前固定为该实现 |
| `ConversationMemory` | `RedisConversationMemory` | Redis 可用性 |
| `VectorStoreStrategy` | `MilvusVectorStoreStrategy` / `GenericVectorStoreStrategy` | `spring.ai.vectorstore.milvus.client.host` |

> ⏱️ **超时注意。** Native 链路处于一个刻意设计的阶梯：上游模型读超时 60s < `orTimeout(65, TimeUnit.SECONDS)` < `spring.mvc.async.request-timeout=90000`，因此 LLM 变慢时前端能拿到可读的中文错误而不是裸 503。`/api/agent/chat` 是**同步**端点，65s 和 90s 两级均不适用于它；只有单个出向调用各自有上限（生成 60s、rerank 20s），整轮（3 次检索 + rerank + 生成）没有请求级预算——这也是 DeepEval 的 agent 客户端将超时设为 90s 的原因。若要对齐 Native，需把入口也改为 `CompletableFuture`。详见 [Architecture.md §3.8](Architecture.md)。

---

## 🌐 REST 端点总表

| 方法 | 路径 | 作用 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/chat/startChat` | 创建会话 | 返回 `{ "chatId": "<uuid>" }`；同时作为 agent 的 `sessionId` 使用 |
| `POST` | `/chat/{chatId}` | Native 提问 | 请求体 `{ "prompt": "..." }`，返回 `{ "message": "..." }` |
| `GET` | `/chat/history/{chatId}` | 对话历史 | 返回 `{ sessionId, messages: [{ role, content }] }` |
| `POST` | `/api/agent/chat` | Agentic 提问 | 请求体 `{ sessionId, question, topK? }`；响应中的 `answer` 由前端复制为 `message`，后端并不接收 `message` 入参 |
| `GET` | `/api/agent/history/{sessionId}` | Agentic 历史 | 与 Native 共用 `ConversationHistoryService` |
| `POST` | `/retrieve` | 仅检索、不生成 | 供 DeepEval 计算 Context Recall |
| `GET` | `/admin/bm25/status` | BM25 索引自检 | 内存/Redis/Milvus 三处计数、健康与一致性判定 |
| `POST` | `/admin/bm25/rebuild` | 从原始语料重建 BM25 | 返回 `{ success, bm25DocumentCount, message, error }`，失败为 500 |
| `POST` | `/documents/upload` | 上传入口 | **占位实现**，始终返回 `Document upload not supported` |

两条通道读写同一份 Redis 会话存储，因此 `/chat/startChat` 返回的 `chatId` 可以直接当 agent `sessionId` 用，反之亦然；前端入口页依赖这一点。

---

## 📥 文档如何进入索引

`RagDataLoader` 是一个 `ApplicationRunner`，启动时先统计集合记录数，**已有 ≥ 10 条则跳过整个摄取流程**。

需要注意 `src/main/resources/data/` 在本仓库中是空目录，真实的摄取链路是远程下载：

1. `FileDownloader.DATA_BEERS_JSON_GZ` 拉取 `https://arxiv.org/pdf/2506.10380`（TableRAG 论文），落为临时文件 `beers-<随机数>.pdf`。
2. `deepseek-ocr.enabled=true` 时，`DeepSeekOcrDocumentReader` 以 100 DPI 光栅化每页、按 0.9 质量编为 JPEG，用 4 个线程发往硅基流动 DeepSeek-OCR 得到 Markdown。`ocr-output/` 下的文件正是这一步的产物。
3. `MarkdownProcessor` 按标题、段落、表格切块，`chunkTokenNum=800`（jtokkit `CL100K_BASE` 计数）；`TokenLimitEnforcer` 再对超过 6000 token 安全阈值的块二次切分，对齐 `TokenCountBatchingStrategy` 的 7372 token 上限。
4. 每个切块同时写入向量库**和** Lucene BM25 索引；索引持久化到 Redis，形如 `bm25:doc:{docId}`（正文截断 2000 字符）加 `bm25:index:docs` 集合。

将 `deepseek-ocr.enabled` 置为 `false` 可回退到纯 PDFBox 文本抽取；`.docx` 走 `RAGFlowDocxParser`，JSON 走 `JsonReader`。

---

## 🧪 评测体系

仓库内有两套互相独立的评测栈，衡量的维度不同。

### Python / DeepEval——[`DeepEvalSpringAIRAG/`](DeepEvalSpringAIRAG/)

```bash
cd DeepEvalSpringAIRAG
pip install -r requirements.txt    # deepeval>=2.0、pymilvus、openai、pytest
python 1_generate_testset.py       # Synthesizer 生成 goldens -> rag_test_dataset.json
python -m src.main                 # -> evaluation_report.json
python -m pytest tests -q          # 评测工具自身的单测
```

需要 `SILICONFLOW_KEY`。goldens 从 Milvus 集合 `paper_collection_vpdf` 的切块合成，Context Recall 通过 `POST /retrieve` 打分、阈值 `0.7`；`src/agent_client.py` 还会经 `POST /api/agent/chat` 跑完整的 agent 端到端链路，客户端超时 90s。

### Java / LLM-as-a-Judge——[`src/test/java/com/redis/demo/spring/ai/`](src/test/java/com/redis/demo/spring/ai/)

| 测试类 | 数据集 | 裁判配置 |
| --- | --- | --- |
| `FaithfulnessEvaluationTest` | `test-data/faithfulness-test-cases.json` | `evaluation.factuality.threshold=0.8` |
| `CorrectnessEvaluationTest` | `test-data/correctness-test-cases.json` | `evaluation.relevance.threshold=0.7` |
| `RagEvaluationTest` | `test-data/rag-test-cases.json` | 检索层校验 |

它们标注了 `@TestPropertySource("classpath:application-evaluation.properties")` 与 `@EnabledIfEnvironmentVariable(named = "SILICONFLOW_KEY", matches = ".+")`，因此没配 Key 时会直接跳过而非失败。裁判模型为 `THUDM/GLM-Z1-9B-0414`。

> ⚠️ 参考评分前先看两条限定。仓库里提交的 `evaluation_report.json` 当前为 `0.0`，且 goldens 被 judge 提示文本污染；ID 命中率 helper 传入的是空检索列表，永远返回 `0`。另外 Python 侧默认连的是 Milvus `10.1.1.234`，而后端 `application.properties` 指向 `192.168.0.223`——评测的集合与实际服务的集合可能不是同一个。

```bash
mvn clean verify                            # 单测 + *IntegrationTest，生成 JaCoCo 报告
mvn test -Dtest=FaithfulnessEvaluationTest   # 裁判类测试，需 SILICONFLOW_KEY
```

---

## 💻 环境依赖要求

为了避免环境冲突，请确保您的开发和运行环境满足以下最低兼容版本：

* **Java**：`JDK 17` 及以上（Spring Boot 4.2 的最低要求；`Dockerfile` 运行镜像为 Eclipse Temurin 21）
* **Maven**：`3.6.3` 及以上
* **Node.js**：`v18.16.0` 及以上（推荐 LTS 版本）
* **npm**：`9.5.1` 及以上
* **Docker**：最新稳定版
* **Docker Compose**：`v2.0.0` 及以上

---

## 🚀 本地部署与启动步骤

适配 Windows、macOS、Linux 主流开发环境的可直接执行指令如下。

### 1. 启动基础设施依赖（Milvus、Redis 等）

利用 Docker Compose 一键启动依赖组件：

```bash
cd spring-ai-redis-demo
docker-compose up -d
```

Compose 文件已在 Redis 旁内置 Milvus v2.6.7 单机版所需的三个容器：

| 服务 | 镜像 | 端口 |
| --- | --- | --- |
| `etcd` | `quay.io/coreos/etcd:v3.5.5` | `2379`（仅 compose 网络内访问） |
| `minio` | `minio/minio:RELEASE.2023-03-20T20-16-18Z` | `9000`（S3）/ `9001`（控制台） |
| `standalone` | `milvusdb/milvus:v2.6.7` | `19530`（gRPC）/ `9091`（metrics） |

若只需先启动向量数据库：

```bash
docker-compose up -d etcd minio standalone
docker-compose logs -f standalone
```

`standalone` 没有配置 healthcheck，`docker-compose ps` 只会显示 `running`；请等到 `19530` 端口真正监听后再启动后端。

后端在宿主机运行时，`spring.ai.vectorstore.milvus.client.host` 填 `127.0.0.1`；跑在同一 compose 网络内时填服务名 `standalone`（`spring` 服务已通过 `SPRING_AI_VECTORSTORE_MILVUS_CLIENT_HOST` 覆盖）。端口在 `RagConfiguration` 中固定为 `19530`。镜像源 retag、数据卷与安全提示、停止清理步骤详见 [DOCKER-README.md](DOCKER-README.md)。

> ⚠️ `RagConfiguration` 硬编码了集合参数——`MetricType.COSINE`、`IndexType.IVF_FLAT`、`databaseName("default")` 与端口 `19530`。像 `spring.ai.vectorstore.milvus.metric-type=IP` 这样的配置项无人读取，改了也**不生效**。实际生效的只有 `embeddingDimension` 与 host / collection-name。

> 🧟 遗留耦合：`RagConfiguration` 仍注入了 `${spring.ai.vectorstore.pinecone.index-name}` 且**没有默认值**，所以尽管已不再使用 Pinecone，`application.properties` 里的 `spring.ai.vectorstore.pinecone.*` 仍必须保留——删掉会导致应用**启动失败**。

### 2. 下载并运行本地模型（Ollama）

确保已安装 [Ollama](https://ollama.com/)：

```bash
# 下载嵌入模型（1024 维向量）
ollama pull bge-m3:latest

# 下载可选的本地对话模型
ollama pull qwen2.5:0.5b
```

### 3. 配置环境变量

如需通过硅基流动使用远程 Chat、Rerank 或 DeepSeek-OCR：

```bash
# Windows
set SILICONFLOW_KEY=你的硅基流动API_KEY

# macOS / Linux
export SILICONFLOW_KEY=你的硅基流动API_KEY
```

如需使用 Redis Cloud 或远程 Redis：

```bash
export REDIS_HOST=你的Redis地址
export REDIS_PORT=6379
export REDIS_PASSWORD=你的Redis密码
```

### 4. 构建前端

Maven **不会**替你构建前端：`pom.xml` 只有一个 `frontend-maven-plugin.version` 属性而无插件声明。实际是 `maven-resources-plugin` 在 `process-classes` 阶段将 `frontend/build` 拷进 `target/classes/static`，因此产物必须事先存在。

```bash
cd frontend
npm install
npm run build        # 生成 frontend/build
```

如果只想调试界面、不想反复重打 jar，改用 `npm start`（CRA 会代理到 `:8080`）。

### 5. 编译并启动后端

仓库内**没有 Maven Wrapper**（不存在 `mvnw` / `mvnw.cmd`），请直接使用本地 Maven：

```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

应用启动后监听 `8080` 端口。访问 [http://localhost:8080](http://localhost:8080) 即可看到 React 界面，入口页可选择 Native 或 Agentic 通道。

> 🐳 `Dockerfile` 的构建阶段仍在执行 `./mvnw verify`，在补上 Maven Wrapper 或改为 `mvn verify` 之前，该镜像构建路径跑不通。

---

## 📂 项目结构说明

```text
spring-ai-redis-demo/
├── frontend/                         # React（CRA）前端
│   ├── src/Components/               # EntryPage（通道选择页）、ChatWindow、ChatBubble
│   ├── src/Services/ChatService.js   # 流式 / 传统聊天传输
│   ├── src/api.js                    # 所有后端调用的唯一出口，统一 ok 标志契约
│   └── build/                        # npm 产物，由 Maven 拷入 jar
├── src/main/java/com/redis/demo/spring/ai/
│   ├── RagApplication.java           # Spring Boot 启动类
│   ├── RagController.java            # Native 通道 + /retrieve + /admin/bm25/*
│   ├── AgentChatController.java      # Agentic 通道（/api/agent/**）
│   ├── RagConfiguration.java         # Milvus 客户端、向量库与 RetrievalTool Bean
│   ├── RagDataLoader.java            # ApplicationRunner：计数检查、摄取、BM25 重建
│   ├── agent/                        # 有界 agent 运行时：QueryPlanner、RetrievalTool、
│   │                                 # ContextBuilder、EvidenceMapper、GroundingChecker、
│   │                                 # AnswerGenerator、AgentRuntimeService
│   ├── service/                      # RagService、HybridSearchService、BM25SearchService、
│   │                                 # RerankService、BM25DocumentPersistenceService、
│   │                                 # ConversationHistoryService、DeepSeekOcrDocumentReader、
│   │                                 # HybridDocumentService、VectorStoreStrategy*
│   ├── memory/                       # ConversationMemory / RedisConversationMemory
│   ├── model/                        # 请求/响应 record 与 Evidence / RetrievalResult
│   ├── parse/                        # RAGFlowDocxParser
│   ├── util/                         # FileDownloader、MarkdownProcessor、TokenLimitEnforcer、
│   │                                 # ImageExtractor/Associator、OllamaRestClientFactory
│   └── config/                       # ChatModelConfiguration、SSLConfig
├── src/main/resources/
│   ├── application.properties
│   ├── prompts/system-qa.st          # Native 通道的系统提示模板
│   └── static/                       # 对外服务的前端（构建期填充）
├── src/test/                         # 单测、集成测试与 LLM-as-a-Judge 评测测试
├── DeepEvalSpringAIRAG/              # Python DeepEval 评测子系统（独立）
│   ├── src/{main,config,agent_client}.py
│   ├── src/services/                 # evaluator、spring_ai_service、milvus_service
│   ├── 1_generate_testset.py         # golden 集合成
│   └── tests/                        # 评测工具自身的 pytest 套件
├── ocr-output/                       # DeepSeek-OCR 摄取链路产出的 Markdown
├── Architecture.md                   # 完整架构 / 数据流 / 数据结构参考
├── Dockerfile                        # 多阶段容器构建
├── docker-compose.yml                # etcd + MinIO + Milvus + Redis Stack + 应用
└── pom.xml                           # Maven 构建与依赖管理
```

---

## 📜 开发规范

1. **分支管理**：
    - `main`：用于发布稳定版本，不允许直接 Push。
    - `develop`：开发主分支。
    - `feature/*`：新功能分支，从 `develop` 检出，开发完成后合并回 `develop`。
2. **提交规范**：
    - 采用 Angular 规范格式：`<type>(<scope>): <subject>`（例如 `feat(rag): 增加 RRF 混合检索算法`、`fix(ui): 修复聊天气泡溢出问题`）。
3. **代码风格**：
    - Java：遵循 Spring Boot 官方编码规范，并利用 Lombok `@Slf4j`、`@Data` 简化代码。
    - 前端：遵循 ESLint + Prettier，采用函数式组件与 Hooks 为主（在逐步迁移旧 Class 组件）。
4. **组件职责**：
    - 保持单一职责原则。Controller 层只处理 HTTP 请求包装；大模型提示拼接、检索流程交由 Service 层处理。

---

## ❓ 常见问题排查（Troubleshooting）

1. **页面空白或前端改动不生效**
    - **问题表现**：执行 `mvn spring-boot:run` 后界面仍是旧版。
    - **原因**：应用服务的是 `target/classes/static`，它在 `process-classes` 阶段从 `frontend/build` 拷入；Maven 根本不会调 npm。
    - **解决方案**：先在 `frontend/` 执行 `npm run build` 再重新构建。平时调试 UI 建议单独 `npm start`，后端另行启动。

2. **找不到 `mvnw`**
    - **问题表现**：按旧说明操作或构建镜像时报 `./mvnw: No such file or directory`。
    - **解决方案**：仓库未提交 Maven Wrapper，请改用本地 `mvn`（3.6.3+）。`Dockerfile` 构建阶段同样引用了 `./mvnw verify`，需一并修正。

3. **Milvus 连接被拒绝 / Connection Refused**
    - **问题表现**：后端启动时抛出 `Failed to connect to Milvus` 异常。
    - **解决方案**：检查 Docker Compose 是否已完全启动（`docker ps` 确保 Milvus 状态为 `healthy` 或 `running`）；`standalone` 容器启动较慢，可用 `docker-compose logs standalone` 确认 `19530` 已开始监听；若 Milvus 部署在远程，请确保 `application.properties` 中的 `spring.ai.vectorstore.milvus.client.host` 地址可达。

4. **重启后检索质量下降**
    - **问题表现**：清空 Redis 或重启应用后，混合结果排序发生变化，证据条目丢失页码与来源。
    - **原因**：`BM25DocumentPersistenceService.loadDocuments()` 以 `new Document(text, new HashMap<>())` 重建文档，同时丢失原始 `docId` 与 metadata。RRF 正是按 `docId` 聚合两路结果，融合实际发生在全新的 UUID 上。
    - **临时绕法**：启动后调用 `POST /admin/bm25/rebuild`，让 Lucene 索引从原始语料重建而不是从 Redis 恢复。

5. **Agentic 请求返回 503 或网关超时**
    - **原因**：`/api/agent/chat` 为同步端点，不受 `spring.mvc.async.request-timeout` 保护，也没有请求级截止；一轮可能发起 3 次检索 + rerank + 生成，仅每个出向调用各自有上限。
    - **解决方案**：调高调用方超时（浏览器代理或 `DeepEvalSpringAIRAG/src/agent_client.py` 的 90s）、减小 `topK`，或在排查期间关闭 `rerank.enabled`。

6. **Ollama 模型拉取失败或超时**
    - **问题表现**：对话或生成 Embedding 时后端日志提示模型调用超时或 404。
    - **解决方案**：通过 `ollama pull <model_name>` 将 `bge-m3:latest` 或 `qwen2.5:0.5b` 拉取到本地，并检查 `http://127.0.0.1:11434` 是否存活。Embedding 保留 `spring.ai.ollama.embedding.read-timeout=300s`；从 Spring Boot 4.2 起，若不显式设置，默认的 Reactor Netty 请求工厂会带来约 10 秒的实际读超时。

7. **远程 API Key 缺失或被拒绝**
    - **问题表现**：Chat、Rerank 或 DeepSeek-OCR 调用出现鉴权或授权错误。
    - **解决方案**：设置 `SILICONFLOW_KEY`，并确认该 Key 有权访问配置中的模型。优先级上 `SILICONFLOW_KEY` 高于 `OPENAI_API_KEY` 与 `siliconflow.api-key`。

8. **Redis OOM（内存溢出）**
    - **问题表现**：加载海量数据建立 BM25 索引持久化时 Redis 崩溃。
    - **解决方案**：后端应用具备降级到本地 Lucene 内存索引的机制（持久化服务会将 `redisAvailable` 置为 false）；请监控 Redis 内存分配，如有需要可在 `docker-compose.yml` 中调整 Redis 内存限制。

9. **评测得分读到 `0.0`**
    - **解决方案**：不要将仓库自带的 `evaluation_report.json` 当作质量信号（限定条件见「评测体系」一节）；将 `DeepEvalSpringAIRAG/src/config.py` 指向后端实际使用的同一个 Milvus 实例，并重新生成 `rag_test_dataset.json` 后再解读 Context Recall。

---

## 📚 相关文档

| 文档 | 内容 |
| --- | --- |
| [Architecture.md](Architecture.md) | 组件拓扑、条件装配矩阵、八条数据流、数据结构与 metadata 字典、存储层布局（Milvus / Lucene / Redis）、前后端契约、配置矩阵，以及已知限制与技术债清单 |
| [DOCKER-README.md](DOCKER-README.md) | 镜像源与 retag 步骤、数据卷、凭据、启动顺序、停止清理 |
| [TEST-README.md](TEST-README.md) | 测试套件构成与运行方式 |
| [frontend/front-end.md](frontend/front-end.md) | 前端组件说明 |
| [src/docs/MIGRATION-BOOT-4.2.0-SPRING-AI-2.1.0-RECORD.md](src/docs/MIGRATION-BOOT-4.2.0-SPRING-AI-2.1.0-RECORD.md) | Spring Boot 4.2.0 / Spring AI 2.1.0 升级迁移记录 |
