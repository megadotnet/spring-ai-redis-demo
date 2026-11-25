# Spring AI Redis Demo Technical Architecture and Usage Guide

English | [简体中文](ReadMe_Zhcn.md)

[![zread](https://img.shields.io/badge/Ask_Zread-_.svg?style=flat&color=00b0aa&labelColor=000000&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIHZpZXdCb3g9IjAgMCAxNiAxNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTQuOTYxNTYgMS42MDAxSDIuMjQxNTZDMS44ODgxIDEuNjAwMSAxLjYwMTU2IDEuODg2NjQgMS42MDE1NiAyLjI0MDFWNC45NjAxQzEuNjAxNTYgNS4zMTM1NiAxLjg4ODEgNS42MDAxIDIuMjQxNTYgNS42MDAxSDQuOTYxNTZDNS4zMTUwMiA1LjYwMDEgNS42MDE1NiA1LjMxMzU2IDUuNjAxNTYgNC45NjAxVjIuMjQwMUM1LjYwMTU2IDEuODg2NjQgNS4zMTUwMiAxLjYwMDEgNC45NjE1NiAxLjYwMDFaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00Ljk2MTU2IDEwLjM5OTlIMi4yNDE1NkMxLjg4ODEgMTAuMzk5OSAxLjYwMTU2IDEwLjY4NjQgMS42MDE1NiAxMS4wMzk5VjEzLjc1OTlDMS42MDE1NiAxNC4xMTM0IDEuODg4MSAxNC4zOTk5IDIuMjQxNTYgMTQuMzk5OUg0Ljk2MTU2QzUuMzE1MDIgMTQuMzk5OSA1LjYwMTU2IDE0LjExMzQgNS42MDE1NiAxMy43NTk5VjExLjAzOTlDNS42MDE1NiAxMC42ODY0IDUuMzE1MDIgMTAuMzk5OSA0Ljk2MTU2IDEwLjM5OTlaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik0xMy43NTg0IDEuNjAwMUgxMS4wMzg0QzEwLjY4NSAxLjYwMDEgMTAuMzk4NCAxLjg4NjY0IDEwLjM5ODQgMi4yNDAxVjQuOTYwMUMxMC4zOTg0IDUuMzEzNTYgMTAuNjg1IDUuNjAwMSAxMS4wMzg0IDUuNjAwMUgxMy43NTg0QzE0LjExMTkgNS42MDAxIDE0LjM5ODQgNS4zMTM1NiAxNC4zOTg0IDQuOTYwMVYyLjI0MDFDMTQuMzk4NCAxLjg4NjY0IDE0LjExMTkgMS42MDAxIDEzLjc1ODQgMS42MDAxWiIgZmlsbD0iI2ZmZiIvPgo8cGF0aCBkPSJNNCAxMkwxMiA0TDQgMTJaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00IDEyTDEyIDQiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxLjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPgo8L3N2Zz4K&logoColor=ffffff)](https://zread.ai/megadotnet/spring-ai-redis-demo)

This project is a Retrieval-Augmented Generation (RAG) demonstration application based on Spring Boot 4.2.0 and Spring AI 2.1.0. It integrates the Milvus vector database and Large Language Models (LLMs) to provide an intelligent question-answering system for knowledge documents.

Retrieval is hybrid — Lucene BM25 and Milvus vector search run in parallel and are merged with Reciprocal Rank Fusion, then optionally reranked by an external cross-encoder. On top of that, the application serves **two independent question-answering channels**: a single-shot *Native* pipeline and an *Agentic* bounded-runtime pipeline with query planning, multi-round retrieval and citation grounding.

For the full architectural walkthrough — data flows, data structures, storage layouts and the frontend/backend contract — see [Architecture.md](Architecture.md).

---

## 🛠️ Tech Stack List

The following details the specific technology selections, their versions, and core roles for each core module in this project.

### 1. 🎨 Frontend Framework

| Technology/Component | Version | Core Role Description |
| --- | --- | --- |
| **React** | `18.2.0` | Core view layer library, building the user interface using functional and Class Component architectures. |
| **React DOM** | `18.2.0` | The renderer that renders React's virtual DOM to the actual DOM in the web environment. |
| **Bootstrap** | `5.3.2` | Provides responsive layout, preset styles, and a foundational CSS framework. |
| **React Bootstrap** | `2.9.2` | Encapsulates Bootstrap components as React components, facilitating state-driven UI rendering. |

### 2. ⚙️ Backend Language and Runtime Environment

| Technology/Component | Version | Core Role Description |
| --- | --- | --- |
| **Java** | `17` | The core backend programming language, providing strong ecosystem support and performance guarantees. |
| **Spring Boot** | `4.2.0` | Application scaffolding, enabling auto-configuration and rapid construction of production-grade RESTful API services. |
| **Spring AI** | `2.1.0` | Unified abstraction for interacting with LLMs and vector databases, supporting Ollama, OpenAI, and Milvus. |
| **Apache Lucene** | `9.11.1` | Powerful text search engine library, used to implement localized, high-performance BM25 keyword search. |
| **Apache POI** | `5.2.3` | Office document parsing library (`poi` and `poi-ooxml`), assisting in parsing imported structured documents. |

### 3. 🗄️ Database Systems and Infrastructure

| Technology/Component | Version | Core Role Description |
| --- | --- | --- |
| **Milvus** | Client SDK `2.6.21` | High-performance vector database, storing high-dimensional embeddings and implementing fast Top-K semantic similarity search. The `milvus-sdk-java` client is provided transitively by `spring-ai-starter-vector-store-milvus`; `docker-compose.yml` deploys the `milvusdb/milvus:v2.6.7` standalone stack (etcd + MinIO + standalone), which is also the image the Testcontainers integration test uses. |
| **Redis** | Redis Stack Server | Used as a high-performance KV cache, and through Spring Data Redis, it persists the BM25 full-text index, supporting index recovery after application restarts. |
| **Ollama** | Latest Stable | Locally run LLM engine, used to execute language models like `qwen2.5:0.5b` and embedding models like `bge-m3:latest`. |

### 4. 🧰 Toolchain and Build/Deployment

| Technology/Component | Version | Core Role Description |
| --- | --- | --- |
| **Maven** | `3.6.3+` | Backend project dependency management and build tool. |
| **Node.js & npm** | Node `v18.16.0`+, npm `9.5.1`+ | Frontend runtime environment and package management tool. |
| **maven-resources-plugin** | Maven default | Copies an already-built `frontend/build` into `target/classes/static` during the `process-classes` phase, so the React SPA ships inside the same Spring Boot jar. **Note:** `pom.xml` declares a `frontend-maven-plugin.version` property but has no matching `<plugin>` block, so Maven never downloads Node nor runs `npm run build` — the frontend must be built separately. |
| **Docker & Compose** | `20+` | Containerization technology, orchestrating the deployment environment of Redis, Milvus, and the application itself to resolve dependency conflicts. |
| **JaCoCo** | `0.8.16` | Code coverage tool, generating statistical reports during the testing phase to ensure code quality. |
| **Testcontainers** | `1.21.4` | Test-scope container runtime for the Milvus integration test. Spring Boot 4.2.0 no longer manages this version, so it is pinned explicitly in `pom.xml`. |

---

## 🤖 Chat, Embedding, and Rerank Model Configuration

The project separates the Chat Model, Embedding Model, and Rerank Model. Their provider and deployment location are configured independently.

| Role | Current default | API location | Main configuration |
| --- | --- | --- | --- |
| Chat Model | SiliconFlow `Qwen/Qwen3-8B` | Remote: `https://api.siliconflow.cn` | `chat.provider=openai`, `spring.ai.openai.*` |
| Embedding Model | Ollama `bge-m3:latest` | Local Ollama: `http://127.0.0.1:11434` | `spring.ai.ollama.base-url`, `spring.ai.ollama.embedding.options.model` |
| Rerank Model | SiliconFlow `BAAI/bge-reranker-v2-m3` | Remote: `https://api.siliconflow.cn/v1/rerank` | `rerank.enabled=true`, `rerank.model` |

All model settings are in [`src/main/resources/application.properties`](src/main/resources/application.properties). The Java provider selection is implemented by:

* [`ChatModelConfiguration.java`](src/main/java/com/redis/demo/spring/ai/config/ChatModelConfiguration.java)
* [`RagConfiguration.java`](src/main/java/com/redis/demo/spring/ai/RagConfiguration.java)
* [`RerankService.java`](src/main/java/com/redis/demo/spring/ai/service/RerankService.java)

### 1. Chat Model

The `chat.provider` property switches the Chat Model implementation:

```properties
# ollama or openai
chat.provider=openai
```

* `chat.provider=ollama` uses the local Ollama Chat API. It is configured by `spring.ai.ollama.chat.options.model` and `spring.ai.ollama.base-url`.
* `chat.provider=openai` uses the OpenAI-compatible Chat Completions API. It can be set to official OpenAI or another compatible provider such as SiliconFlow.

The current remote SiliconFlow configuration is:

```properties
spring.ai.openai.api-key=${SILICONFLOW_KEY:}
spring.ai.openai.chat.model=Qwen/Qwen3-8B
spring.ai.openai.chat.options.model=Qwen/Qwen3-8B
spring.ai.openai.base-url=https://api.siliconflow.cn
spring.ai.openai.chat.options.temperature=0.7
```

Set the API key before starting the application:

```bash
# macOS / Linux
export SILICONFLOW_KEY=your_siliconflow_api_key

# Windows Command Prompt
set SILICONFLOW_KEY=your_siliconflow_api_key
```

To switch back to local Ollama chat generation:

```properties
chat.provider=ollama
spring.ai.ollama.base-url=http://127.0.0.1:11434
spring.ai.ollama.chat.options.model=qwen2.5:0.5b
spring.ai.ollama.connection-timeout=60s
spring.ai.ollama.read-timeout=60s
```

Download the local model first:

```bash
ollama pull qwen2.5:0.5b
ollama serve
```

### 2. Embedding Model

The Embedding Model is currently independent of `chat.provider` and always uses Ollama:

```properties
spring.ai.ollama.base-url=http://127.0.0.1:11434
spring.ai.ollama.embedding.options.model=bge-m3:latest
spring.ai.ollama.embedding.connect-timeout=10s
spring.ai.ollama.embedding.read-timeout=300s
```

This means document ingestion and user query embedding are sent to Ollama on `127.0.0.1:11434`. Change `spring.ai.ollama.base-url` when Ollama runs in a container or on another host.

Since Spring Boot 4.2, the default request factory behind `OllamaApi` is Reactor Netty, which applies an effective read timeout of about 10 seconds, so the embedding timeouts above are set explicitly instead of relying on `spring.ai.ollama.read-timeout`.

```bash
ollama pull bge-m3:latest
ollama serve
```

The vector dimension must match the embedding model. The current Milvus configuration uses 1024 dimensions:

```properties
spring.ai.vectorstore.milvus.embeddingDimension=1024
```

### 3. Rerank Model

Reranking is disabled unless explicitly enabled:

```properties
rerank.enabled=true
rerank.model=BAAI/bge-reranker-v2-m3
rerank.topN=5
```

The rerank call is sent to the remote SiliconFlow endpoint `https://api.siliconflow.cn/v1/rerank` using `SILICONFLOW_KEY`. If the API call fails, `RerankService` falls back to the original candidate order and returns the configured `topN` documents.

The API key is resolved in this order:

1. `SILICONFLOW_KEY` environment variable
2. `siliconflow.api-key` property
3. The empty-key fallback, for which the service logs a warning

### DeepSeek-OCR

PDF OCR is a separate remote service and is not a Chat, Embedding, or Rerank Model role. It uses SiliconFlow's remote DeepSeek-OCR API:

```properties
deepseek-ocr.enabled=true
deepseek-ocr.model=deepseek-ai/DeepSeek-OCR
```

It also uses `SILICONFLOW_KEY`. PDF pages are rendered locally, sent as Base64 JPEG images to the remote API, and converted to Markdown before chunking.

---

## 🔀 Two Question-Answering Channels

Both channels share the same retrieval stack, the same conversation store and the same React page; they differ in control flow.

| | Native channel | Agentic channel |
| --- | --- | --- |
| Endpoint | `POST /chat/{chatId}` | `POST /api/agent/chat` |
| Service | [`RagService.java`](src/main/java/com/redis/demo/spring/ai/service/RagService.java) | [`AgentRuntimeService.java`](src/main/java/com/redis/demo/spring/ai/agent/AgentRuntimeService.java) |
| Execution model | Asynchronous (`CompletableFuture`) | Synchronous |
| Retrieval rounds | exactly 1 | at most `MAX_TOOL_CALLS = 3` |
| Query planning | none — the raw question is searched | `QueryPlanner` splits compound questions into sub-queries |
| Request body | `{ "prompt": "..." }` | `{ "sessionId", "question", "topK"? }` |
| Response body | `{ "message": "..." }` | `answer` + `evidence[]` + `plan` + `grounded` + `traceId`; `api.js` mirrors `answer` into a `message` field so `ChatWindow` renders either channel unchanged |
| Citation verification | none | `GroundingChecker` requires at least one `[证据 N]` marker, and every cited ID must resolve to a returned evidence item |
| Frontend entry | `#/native` | `#/agentic` (renders a collapsible evidence panel) |

The Agentic channel is a **bounded** runtime rather than an open-ended tool loop: every ceiling is a compile-time constant in `AgentRuntimeService` (`MAX_TOOL_CALLS = 3`, `DEFAULT_TOP_K = 5`, `MAX_TOP_K = 20`, `MAX_CONTEXT_CHARS = 12_000`), and the control flow stays entirely in Java — the LLM only writes text and never decides which tool to invoke next.

Behaviour is selected through interfaces, so either channel degrades without code changes:

| Interface | Implementations | Selected by |
| --- | --- | --- |
| `RetrievalTool` | `HybridRetrievalTool` / `VectorOnlyRetrievalTool` | `hybrid.search.enabled` |
| `QueryPlanner` | `RuleBasedQueryPlanner` (regex + keyword splitting, no LLM call) | always this one today |
| `ConversationMemory` | `RedisConversationMemory` | Redis availability |
| `VectorStoreStrategy` | `MilvusVectorStoreStrategy` / `GenericVectorStoreStrategy` | `spring.ai.vectorstore.milvus.client.host` |

> ⏱️ **Timeout caveat.** The Native route sits inside a deliberate ladder: upstream model read timeout 60 s < `orTimeout(65, TimeUnit.SECONDS)` < `spring.mvc.async.request-timeout=90000`, so a slow LLM produces a readable Chinese error message instead of a bare 503. `/api/agent/chat` is a **synchronous** handler, therefore neither the 65 s nor the 90 s layer applies to it. Only each individual outbound call is capped (60 s for generation, 20 s for rerank); the turn as a whole — up to 3 retrievals plus rerank plus generation — has no request-level deadline, which is why the DeepEval agent client allows 90 s. Adding a `CompletableFuture` entry there would restore parity with Native. See [Architecture.md §3.8](Architecture.md).

---

## 🌐 REST API Surface

| Method | Path | Purpose | Notes |
| --- | --- | --- | --- |
| `POST` | `/chat/startChat` | Create a session | Returns `{ "chatId": "<uuid>" }`; reused as the agent `sessionId` |
| `POST` | `/chat/{chatId}` | Native question | Body `{ "prompt": "..." }`; returns `{ "message": "..." }` |
| `GET` | `/chat/history/{chatId}` | Conversation history | Returns `{ sessionId, messages: [{ role, content }] }` |
| `POST` | `/api/agent/chat` | Agentic question | Body `{ sessionId, question, topK? }`; the response `answer` is duplicated into `message` by the frontend, not by the backend |
| `GET` | `/api/agent/history/{sessionId}` | Agentic history | Same `ConversationHistoryService` as the Native route |
| `POST` | `/retrieve` | Retrieval only, no generation | Consumed by the DeepEval harness for context recall |
| `GET` | `/admin/bm25/status` | BM25 index health | Indexed document count plus Redis availability flag |
| `POST` | `/admin/bm25/rebuild` | Rebuild BM25 from the source corpus | Returns `{ success, bm25DocumentCount, message, error }` |
| `POST` | `/documents/upload` | Upload hook | **Placeholder** — always answers `Document upload not supported` |

Because both channels read and write the same Redis session store, a `chatId` returned by `/chat/startChat` works as an agent `sessionId` and vice versa; the frontend entry page depends on this.

---

## 📥 How Documents Enter the Index

`RagDataLoader` is an `ApplicationRunner`. On startup it counts the collection records and **skips ingestion when there are already ≥ 10 documents**.

Note that `src/main/resources/data/` is empty in this repository, so the live ingestion path is a remote download:

1. `FileDownloader.DATA_BEERS_JSON_GZ` fetches `https://arxiv.org/pdf/2506.10380` (the TableRAG paper) into a temporary `beers-<random>.pdf`.
2. With `deepseek-ocr.enabled=true`, `DeepSeekOcrDocumentReader` rasterises each page at 100 DPI, encodes JPEG at quality 0.9 and posts it to SiliconFlow DeepSeek-OCR across 4 threads, producing Markdown. The files under `ocr-output/` are by-products of exactly this step.
3. `MarkdownProcessor` splits by heading, paragraph and table using `chunkTokenNum=800` counted with jtokkit `CL100K_BASE`; `TokenLimitEnforcer` then truncates anything beyond the 6 000-token safety threshold, against the `TokenCountBatchingStrategy` limit of 7 372 tokens.
4. Each chunk is written to the vector store **and** to the Lucene BM25 index; the index is persisted to Redis as `bm25:doc:{docId}` (text clipped to 2 000 characters) plus the `bm25:index:docs` set.

Disable `deepseek-ocr.enabled` to fall back to plain PDFBox text extraction. `.docx` files go through `RAGFlowDocxParser` and JSON through `JsonReader`.

---

## 🧪 Evaluation Harness

Two independent stacks exist and they measure different things.

### Python / DeepEval — [`DeepEvalSpringAIRAG/`](DeepEvalSpringAIRAG/)

```bash
cd DeepEvalSpringAIRAG
pip install -r requirements.txt    # deepeval>=2.0, pymilvus, openai, pytest
python 1_generate_testset.py       # Synthesizer goldens -> rag_test_dataset.json
python -m src.main                 # -> evaluation_report.json
python -m pytest tests -q          # unit tests for the harness itself
```

Requires `SILICONFLOW_KEY`. Goldens are synthesised from Milvus chunks (`paper_collection_vpdf`), Context Recall is scored against a `0.7` threshold through `POST /retrieve`, and `src/agent_client.py` additionally drives the full agent end-to-end via `POST /api/agent/chat` with a 90 s client timeout.

### Java / LLM-as-a-Judge — [`src/test/java/com/redis/demo/spring/ai/`](src/test/java/com/redis/demo/spring/ai/)

| Test class | Fixture | Judge configuration |
| --- | --- | --- |
| `FaithfulnessEvaluationTest` | `test-data/faithfulness-test-cases.json` | `evaluation.factuality.threshold=0.8` |
| `CorrectnessEvaluationTest` | `test-data/correctness-test-cases.json` | `evaluation.relevance.threshold=0.7` |
| `RagEvaluationTest` | `test-data/rag-test-cases.json` | retrieval-level checks |

They are annotated `@TestPropertySource("classpath:application-evaluation.properties")` and `@EnabledIfEnvironmentVariable(named = "SILICONFLOW_KEY", matches = ".+")`, so they silently skip without a key. The judge model is `THUDM/GLM-Z1-9B-0414`.

> ⚠️ Two caveats before trusting any score. The committed `evaluation_report.json` currently reports `0.0` and its goldens are polluted with judge prompt text; the ID-hit-rate helper is invoked with an empty retrieved list, so it always returns `0`. Separately, the Python harness defaults to Milvus at `10.1.1.234` while `application.properties` points at `192.168.0.223` — the evaluated collection may not be the served one.

```bash
mvn clean verify                        # unit + *IntegrationTest, JaCoCo report
mvn test -Dtest=FaithfulnessEvaluationTest   # judge tests, requires SILICONFLOW_KEY
```

---

## 💻 Environmental Dependency Requirements

To avoid environmental conflicts, please ensure your development and runtime environments meet the following minimum compatible versions:

* **Java**: `JDK 17` or higher (the minimum required by Spring Boot 4.2; the `Dockerfile` runtime image is Eclipse Temurin 21)
* **Maven**: `3.6.3` or higher
* **Node.js**: `v18.16.0` or higher (LTS version recommended)
* **npm**: `9.5.1` or higher
* **Docker**: Latest stable version
* **Docker Compose**: `v2.0.0` or higher

---

## 🚀 Local Deployment and Startup Steps

The directly executable instructions adapted for Windows, macOS, and Linux mainstream development environments are as follows.

### 1. Start Infrastructure Dependencies (Milvus, Redis, etc.)

Use Docker Compose to start the dependency components with one click:

```bash
cd spring-ai-redis-demo
docker-compose up -d
```

The Compose file ships the Milvus v2.6.7 standalone stack next to Redis:

| Service | Image | Ports |
| --- | --- | --- |
| `etcd` | `quay.io/coreos/etcd:v3.5.5` | `2379` (compose network only) |
| `minio` | `minio/minio:RELEASE.2023-03-20T20-16-18Z` | `9000` (S3) / `9001` (console) |
| `standalone` | `milvusdb/milvus:v2.6.7` | `19530` (gRPC) / `9091` (metrics) |

To start only the vector database:

```bash
docker-compose up -d etcd minio standalone
docker-compose logs -f standalone
```

`standalone` has no healthcheck, so `docker-compose ps` only reports `running`; wait until port `19530` is actually listening before launching the backend.

Set `spring.ai.vectorstore.milvus.client.host` to `127.0.0.1` when the backend runs on the host, or to the service name `standalone` when it runs inside this compose network (the `spring` service already overrides it through `SPRING_AI_VECTORSTORE_MILVUS_CLIENT_HOST`). The port is fixed at `19530` in `RagConfiguration`. Image mirrors, data volumes, credentials, and cleanup steps are documented in [DOCKER-README.md](DOCKER-README.md).

> ⚠️ `RagConfiguration` hard-codes the collection settings — `MetricType.COSINE`, `IndexType.IVF_FLAT`, `databaseName("default")` and port `19530`. Properties such as `spring.ai.vectorstore.milvus.metric-type=IP` are read by nobody and changing them has **no effect**. Only `embeddingDimension` and the host/collection name are actually honoured.

> 🧟 Leftover coupling: `RagConfiguration` still injects `${spring.ai.vectorstore.pinecone.index-name}` with **no default value**, so the Pinecone properties in `application.properties` must be kept even though Pinecone is not used — deleting them makes the application fail to start.

### 2. Download and Run Local Models (Ollama)

Ensure you have installed [Ollama](https://ollama.com/):

```bash
# Download and run the embedding model (1024-dimensional vectors)
ollama pull bge-m3:latest

# Download and run the optional local chat model
ollama pull qwen2.5:0.5b
```

### 3. Configure Environment Variables

For remote Chat, Rerank, and DeepSeek-OOCR usage through SiliconFlow:

```bash
# Windows
set SILICONFLOW_KEY=your_siliconflow_api_key

# macOS / Linux
export SILICONFLOW_KEY=your_siliconflow_api_key
```

For Redis Cloud or a remote Redis server:

```bash
export REDIS_HOST=your_redis_host
export REDIS_PORT=6379
export REDIS_PASSWORD=your_redis_password
```

### 4. Build the Frontend

Maven does **not** build the frontend for you: `pom.xml` only carries a `frontend-maven-plugin.version` property with no plugin declaration. Instead, `maven-resources-plugin` copies `frontend/build` into `target/classes/static` at the `process-classes` phase — so the bundle must exist beforehand.

```bash
cd frontend
npm install
npm run build        # produces frontend/build
```

To iterate on the UI without rebuilding the jar, run the dev server instead (`npm start`, CRA proxies to `:8080`).

### 5. Compile and Start the Backend

The repository has **no Maven Wrapper** (`mvnw` / `mvnw.cmd` are absent), so use a local Maven installation:

```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

Once the application starts it listens on port `8080`. Visit [http://localhost:8080](http://localhost:8080) for the React interface; the entry page lets you choose between the Native and the Agentic channel.

> 🐳 The `Dockerfile` still runs `./mvnw verify` in its build stage and will therefore fail until a Maven Wrapper is committed or the line is changed to `mvn verify`.

---

## 📂 Project Structure

```text
spring-ai-redis-demo/
├── frontend/                         # React (CRA) frontend
│   ├── src/Components/               # EntryPage (channel picker), ChatWindow, ChatBubble
│   ├── src/Services/ChatService.js   # streaming / legacy chat transport
│   ├── src/api.js                    # single entry point for all backend calls, ok-flag contract
│   └── build/                        # npm output, copied into the jar by Maven
├── src/main/java/com/redis/demo/spring/ai/
│   ├── RagApplication.java           # Spring Boot entry point
│   ├── RagController.java            # Native channel + /retrieve + /admin/bm25/*
│   ├── AgentChatController.java      # Agentic channel (/api/agent/**)
│   ├── RagConfiguration.java         # Milvus client, vector store and RetrievalTool beans
│   ├── RagDataLoader.java            # ApplicationRunner: count check, ingest, BM25 rebuild
│   ├── agent/                        # bounded agent runtime: QueryPlanner, RetrievalTool,
│   │                                 # ContextBuilder, EvidenceMapper, GroundingChecker,
│   │                                 # AnswerGenerator, AgentRuntimeService
│   ├── service/                      # RagService, HybridSearchService, BM25SearchService,
│   │                                 # RerankService, BM25DocumentPersistenceService,
│   │                                 # ConversationHistoryService, DeepSeekOcrDocumentReader,
│   │                                 # HybridDocumentService, VectorStoreStrategy*
│   ├── memory/                       # ConversationMemory / RedisConversationMemory
│   ├── model/                        # request/response records and Evidence / RetrievalResult
│   ├── parse/                        # RAGFlowDocxParser
│   ├── util/                         # FileDownloader, MarkdownProcessor, TokenLimitEnforcer,
│   │                                 # ImageExtractor/Associator, OllamaRestClientFactory
│   └── config/                       # ChatModelConfiguration, SSLConfig
├── src/main/resources/
│   ├── application.properties
│   ├── prompts/system-qa.st          # Native channel prompt template
│   └── static/                       # served frontend (populated at build time)
├── src/test/                         # unit, integration and LLM-as-a-Judge evaluation tests
├── DeepEvalSpringAIRAG/              # Python DeepEval harness (independent subsystem)
│   ├── src/{main,config,agent_client}.py
│   ├── src/services/                 # evaluator, spring_ai_service, milvus_service
│   ├── 1_generate_testset.py         # golden-set synthesiser
│   └── tests/                        # pytest suite for the harness itself
├── ocr-output/                       # Markdown produced by the DeepSeek-OCR ingestion path
├── Architecture.md                   # full architecture / data-flow / data-structure reference
├── Dockerfile                        # multi-stage container build
├── docker-compose.yml                # etcd + MinIO + Milvus + Redis Stack + application
└── pom.xml                           # Maven build and dependency management
```

---

## 📜 Development Guidelines

1. **Branch Management**:
    - `main`: Used for releasing stable versions, direct Push is not allowed.
    - `develop`: Main development branch.
    - `feature/*`: New feature branches, checked out from `develop` and merged back into `develop` upon completion.
2. **Commit Conventions**:
    - Adopt Angular specification formats: `<type>(<scope>): <subject>` (e.g., `feat(rag): add RRF hybrid search algorithm`, `fix(ui): fix chat bubble overflow issue`).
3. **Code Style**:
    - Java: Follow official Spring Boot coding guidelines, and utilize Lombok `@Slf4j`, `@Data` to simplify code.
    - Frontend: Follow ESLint + Prettier, using functional components and Hooks predominantly (while gradually migrating legacy Class components).
4. **Component Responsibilities**:
    - Maintain the single responsibility principle. The Controller layer should only handle HTTP request packaging; specific LLM prompt concatenation and retrieval processes should be handled by the Service layer.

---

## ❓ Troubleshooting

1. **The served page is stale or blank**
    - **Symptom**: UI changes do not appear after `mvn spring-boot:run`.
    - **Cause**: the jar serves `target/classes/static`, which is populated from `frontend/build` at the `process-classes` phase. Maven never runs `npm`.
    - **Solution**: run `npm run build` in `frontend/`, then rebuild. During UI work, use `npm start` against a separately running backend instead.

2. **`mvnw` not found**
    - **Symptom**: `./mvnw: No such file or directory` when following older instructions or building the Docker image.
    - **Solution**: no Maven Wrapper is committed in this repository. Use a local `mvn` (3.6.3+). The `Dockerfile` build stage still references `./mvnw verify` and needs the same treatment.

3. **Milvus Connection Refused**
    - **Symptom**: Backend throws `Failed to connect to Milvus` exception upon startup.
    - **Solution**: Check if Docker Compose is fully started (`docker ps` to ensure milvus status is `healthy` or `running`); the `standalone` container needs extra time before it serves `19530`, so inspect `docker-compose logs standalone` as well. If the Milvus server is deployed elsewhere, make sure `spring.ai.vectorstore.milvus.client.host` in `application.properties` points to that reachable address.

4. **Retrieval quality drops after a restart**
    - **Symptom**: hybrid results look reordered, or evidence entries lose their page/source information after Redis was flushed or the app restarted.
    - **Cause**: `BM25DocumentPersistenceService.loadDocuments()` restores documents as `new Document(text, new HashMap<>())`, dropping both the original `docId` and the metadata. Because RRF merges the two result lists by `docId`, the fusion is effectively rebuilt on fresh UUIDs.
    - **Workaround**: call `POST /admin/bm25/rebuild` after startup so the Lucene index is regenerated from the source corpus rather than restored from Redis.

5. **Agentic requests fail with 503 or a gateway timeout**
    - **Cause**: `/api/agent/chat` is synchronous, so no request-level deadline applies (see the timeout caveat above); a turn may perform 3 retrievals plus rerank plus generation, and only each outbound call is capped individually.
    - **Solution**: raise `spring.http.client.read-timeout` on the caller side, reduce `topK`, or disable rerank while debugging.

6. **Ollama model pull failed or timed out**
    - **Symptom**: Backend logs indicate model call timeout or 404 during conversation.
    - **Solution**: Ensure you have successfully pulled the model (e.g., `qwen2.5`) locally beforehand via `ollama pull <model_name>`, and check if `http://127.0.0.1:11434` is alive. Note that embeddings keep `spring.ai.ollama.embedding.read-timeout=300s`; since Spring Boot 4.2 the default Reactor Netty request factory imposes roughly a 10 s read timeout when it is not set explicitly.

7. **Remote API Key missing or rejected**
    - **Symptom**: Chat, rerank, or DeepSeek-OCR calls fail with authentication or authorization errors.
    - **Solution**: Set `SILICONFLOW_KEY` and verify that the key has access to the configured models. `SILICONFLOW_KEY` takes precedence over `OPENAI_API_KEY` and over `siliconflow.api-key`.

8. **Redis OOM (Out Of Memory) Issues**
    - **Symptom**: Redis crashes when loading massive data to establish BM25 index persistence.
    - **Solution**: The backend application has a mechanism to automatically downgrade to a local Lucene in-memory index; please monitor Redis memory allocation.

9. **Evaluation scores read as `0.0`**
    - **Solution**: treat the committed `evaluation_report.json` as unreliable (see the caveats under *Evaluation Harness*), point `DeepEvalSpringAIRAG/src/config.py` at the same Milvus instance the backend uses, and regenerate `rag_test_dataset.json` before interpreting Context Recall numbers.

---

## 📚 Further Documentation

| Document | Contents |
| --- | --- |
| [Architecture.md](Architecture.md) | Component topology, conditional bean wiring, all eight data flows, data structures and metadata dictionary, storage layouts (Milvus / Lucene / Redis), the frontend-backend contract, the configuration matrix and a list of known limitations and technical debt |
| [DOCKER-README.md](DOCKER-README.md) | Image mirrors and retag procedure, data volumes, credentials, startup order, cleanup |
| [TEST-README.md](TEST-README.md) | Test suite layout and how to run it |
| [frontend/front-end.md](frontend/front-end.md) | Frontend component notes |
| [src/docs/MIGRATION-BOOT-4.2.0-SPRING-AI-2.1.0-RECORD.md](src/docs/MIGRATION-BOOT-4.2.0-SPRING-AI-2.1.0-RECORD.md) | Migration record for the Spring Boot 4.2.0 / Spring AI 2.1.0 upgrade |
