# Spring AI Redis Demo 技术架构解读报告

> 本文档基于分支 `agentic-rag-implementation-plan-42bc0` 的实际源码逐文件核对生成，覆盖后端（`src/`）、前端（`frontend/`）、评测子系统（`DeepEvalSpringAIRAG/`）三个源码域的技术架构、数据流、数据结构与前后端交互契约。
> 组件版本口径见第 9 节；Boot 4.2.0 / Spring AI 2.1.0 的升级过程与验证记录见 [MIGRATION-BOOT-4.2.0-SPRING-AI-2.1.0-RECORD.md](src/docs/MIGRATION-BOOT-4.2.0-SPRING-AI-2.1.0-RECORD.md)。

## 0. 结论速览

这是一个**双通道 RAG 系统**：同一条知识库（Milvus 向量 + Lucene BM25 + Redis 持久化）之上，并行运行两条问答链路，二者共用同一份 Redis 会话记忆与同一个会话 ID 空间。

| 通道 | 入口 | 控制流归属 | 输出 |
|------|------|------------|------|
| Native RAG（legacy 回滚路径） | `POST /chat/{chatId}` | LLM 一次调用完成检索后生成 | 纯文本回答 |
| Agentic RAG（灰度路径） | `POST /api/agent/chat` | **Java Runtime 控制流程，LLM 只负责生成** | 回答 + 证据 + 执行步骤 + traceId + 引用校验结果 |

| 模块 | 位置 | 职责 | 关键技术 |
|------|------|------|----------|
| 后端应用 | `src/main/java/com/redis/demo/spring/ai/` | REST 服务、检索编排、Agent Runtime、数据摄取 | Spring Boot 4.2.0 + Spring AI 2.1.0 |
| 前端 | `frontend/src/` | 通道选择、对话界面、证据面板 | React 18（CRA）+ React Bootstrap |
| 评测（Python） | `DeepEvalSpringAIRAG/` | 检索质量离线评估、Agent 端到端评估驱动 | DeepEval + pymilvus + SiliconFlow |
| 评测（Java） | `src/test/java/.../evaluation/` | LLM-as-a-Judge 忠实性/正确性回归 | SpringBootTest + SiliconFlow judge 模型 |
| 部署 | `Dockerfile`、`docker-compose.yml` | Milvus 单机三件套 + Redis + 应用 | Docker Compose |

---

## 1. 框架全景图

### 1.1 项目结构

```
spring-ai-redis-demo/
├── frontend/                         # React（CRA）前端；构建产物拷贝进后端 static 目录
│   ├── src/App.js                    # hash 路由：#/native、#/agentic
│   ├── src/Components/               # EntryPage / ChatWindow / ChatBubble
│   ├── src/Services/ChatService.js   # 遗留代码，当前未被引用（见第 8 节）
│   └── src/api.js                    # 前后端契约的唯一出口
├── src/
│   ├── docs/                         # 迁移记录
│   ├── main/java/com/redis/demo/spring/ai/
│   │   ├── RagApplication.java       # 启动类（@Import SSLConfig，main 内 trustAllCertificates）
│   │   ├── RagController.java        # Native 通道 + /retrieve + /admin/bm25/*
│   │   ├── AgentChatController.java  # Agentic 通道（灰度入口）
│   │   ├── RagConfiguration.java     # 向量库/嵌入/Rerank/BM25/RetrievalTool 装配
│   │   ├── RagDataLoader.java        # ApplicationRunner：冷启动摄取 + BM25 重建
│   │   ├── agent/                    # 有界 Agent Runtime（10 个类）
│   │   ├── service/                  # 检索、精排、持久化、历史、文档解析（17 个类）
│   │   ├── memory/                   # ConversationMemory + Redis 实现
│   │   ├── model/                    # 8 个 record DTO
│   │   ├── util/                     # 切块、token 守卫、HTTP 工厂、SSL
│   │   ├── parse/RAGFlowDocxParser   # DOCX 段落/表格解析
│   │   └── config/                   # ChatModel 双供应商切换、SSL
│   └── main/resources/
│       ├── application.properties    # 全量运行期配置
│       ├── prompts/system-qa.st      # Native 通道系统提示
│       ├── data/                     # 空目录（当前无本地数据文件，见 3.1）
│       └── static/                   # 前端构建产物（已入库）
├── src/test/                         # 单测 + 集成测试 + LLM-as-a-Judge 评估
├── DeepEvalSpringAIRAG/              # Python 评测子系统
├── ocr-output/                       # DeepSeek-OCR 产出的 Markdown（运行副产物）
├── Dockerfile / docker-compose.yml
└── pom.xml
```

### 1.2 组件拓扑

```
                       浏览器（React CRA → target/classes/static）
                                   │  fetch（相对路径，dev 期 proxy:8080）
             ┌─────────────────────┴──────────────────────┐
             ▼                                            ▼
   RagController (@Controller)                AgentChatController (@RestController)
   POST /chat/startChat                        POST /api/agent/chat
   POST /chat/{chatId}     [异步]              GET  /api/agent/history/{sessionId}
   GET  /chat/history/{chatId}                            │
   POST /retrieve       ← Python 评测                      │ 只做校验与委托
   GET/POST /admin/bm25/*                                  │
             │                                            ▼
             ▼                                  AgentRuntimeService  ◄── 控制流中枢
        RagService                                  │  硬上限：工具 3 次 / topK 1..20 /
        （检索→生成，无规划无校验）                    │  上下文 12000 字符
             │                                      ├── QueryPlanner (RuleBasedQueryPlanner)
             │                                      ├── RetrievalTool (Hybrid | VectorOnly)
             │                                      ├── ContextBuilder
             │                                      ├── AnswerGenerator
             │                                      ├── GroundingChecker
             │                                      └── ConversationMemory
             └──────────────────┬───────────────────┴─────────────────┐
                                ▼                                     ▼
                    HybridSearchService                    RedisConversationMemory
                    ├─ VectorStore(Milvus)                 key: rag:session:{id}
                    └─ BM25SearchService(Lucene)
                                │                                     │
                        RerankService(SiliconFlow)              StringRedisTemplate
                                                        BM25DocumentPersistenceService
                                                            key: bm25:doc:{id} / bm25:index:docs
```

### 1.3 结构与边界约束

代码里可验证到的四条硬性边界，构成整体架构风格：

1. **Controller 轻量**：两个 Controller 只做参数校验与委托，不拼装业务 Map（[AgentChatController.java](src/main/java/com/redis/demo/spring/ai/AgentChatController.java)、[Bm25AdminService.java](src/main/java/com/redis/demo/spring/ai/service/Bm25AdminService.java)）。
2. **流程控制权不下放给模型**：Agent Runtime 用 Java 循环 + 常量上限约束工具调用，不靠 prompt 要求模型"别循环"（[AgentRuntimeService.java](src/main/java/com/redis/demo/spring/ai/agent/AgentRuntimeService.java)）。
3. **检索能力通过接口收口**：`QueryPlanner`、`RetrievalTool`、`ConversationMemory`、`VectorStoreStrategy`、`DocumentCountProvider` 均为接口 + 可插拔实现，实现的选择集中在 `RagConfiguration` 用配置开关完成。
4. **降级都是结构化的**：每一层失败都落到明确分支（返回空列表 / 原序 / 纯向量 / 拒答文案 / `steps` 标记），不把异常直接抛穿整条链。

---

## 2. 组件清单与装配矩阵

### 2.1 后端包职责

| 包 | 关键类 | 职责 |
|----|--------|------|
| `agent` | `AgentRuntimeService` | 有界 Agent 主循环（读历史→规划→检索→组装→生成→校验→落库） |
| `agent` | `QueryPlanner`(接口) / `RuleBasedQueryPlanner` | 输出检索 query 列表或澄清请求 |
| `agent` | `RetrievalTool`(接口) / `HybridRetrievalTool` / `VectorOnlyRetrievalTool` | Document → Evidence 的检索工具封装 |
| `agent` | `EvidenceMapper`（包私有 final） | Spring AI `Document` → `Evidence` 的统一映射 |
| `agent` | `ContextBuilder` | 去重、按字符预算限长、生成带引用标记的上下文 |
| `agent` | `AnswerGenerator` | 内联系统提示 + `ChatModel.call(Prompt)` |
| `agent` | `GroundingChecker` | 正则回查回答中的引用 ID 合法性 |
| `service` | `RagService` | Native 链路：检索模式选择 → rerank → 提示拼装 → 生成 → 写历史 |
| `service` | `HybridSearchService` | BM25 + 向量并行召回 + RRF 融合 |
| `service` | `BM25SearchService` | Lucene 内存索引（`ByteBuffersDirectory`）+ 读写锁 |
| `service` | `BM25DocumentPersistenceService` | BM25 文档 Redis 持久化 + OOM 熔断 |
| `service` | `Bm25AdminService` | 三处计数汇总、健康/一致性判定、重建编排 |
| `service` | `RerankService` | SiliconFlow `/v1/rerank`（`BAAI/bge-reranker-v2-m3`） |
| `service` | `ConversationHistoryService` | 两通道共用的历史读取与角色归一化 |
| `service` | `HybridDocumentService` | 按扩展名分派文档解析 + 切块算法选择 |
| `service` | `DeepSeekOcrDocumentReader` | PDF → 页面 JPEG → SiliconFlow OCR → Markdown |
| `service` | `Milvus/Redis DocumentCountProvider`、`VectorStoreStrategy*` | 集合计数与 VectorStore 差异屏蔽 |
| `memory` | `ConversationMemory` / `RedisConversationMemory` | Redis List 短期记忆（裁剪 + TTL） |
| `util` | `TokenLimitEnforcer` | 入库前 token 上限守卫与二次切分 |
| `util` | `MarkdownProcessor` / `MarkdownElementExtractor` | Markdown 语义切块、表格抽取与 HTML 化 |
| `util` | `OllamaRestClientFactory` | 显式接管 OllamaApi 内部 RestClient 超时 |
| `util` | `FileDownloader` | 数据源下载（arXiv → 临时 `.pdf`） |

### 2.2 条件装配矩阵

Bean 存在性完全由三个开关决定，这也是所有降级路径的根因：

| 配置项 | 值 | 生效的 Bean | 影响链路 |
|--------|----|-------------|----------|
| `hybrid.search.enabled` | `true` | `BM25DocumentPersistenceService`、`BM25SearchService`、`HybridSearchService` | Native 走混合检索；Agent 用 `HybridRetrievalTool` |
| `hybrid.search.enabled` | `false` | 上述三者均不存在 | Native 纯向量；Agent 用 `VectorOnlyRetrievalTool` |
| `rerank.enabled` | `true` | `RerankService` | 两条链路精排 + Native 的 `rerank.topN` |
| `chat.provider` | `openai`（当前配置值） | `OpenAiChatModel`（`@Primary`） | 生成与 OCR/rerank 共用 SiliconFlow 账号体系 |
| `chat.provider` | `ollama` 或缺省（`matchIfMissing=true`） | `OllamaApi` + `OllamaChatModel` | 本地 `qwen2.5:0.5b` 生成 |
| `deepseek-ocr.enabled` | `true` | —（由 `HybridDocumentService` 直接 new `DeepSeekOcrDocumentReader`） | PDF 摄取路径 |

三个补充事实：

- `RetrievalTool`、`RagService`、`bm25SearchService` 等在 `RagConfiguration` 中通过 `ObjectProvider.getIfAvailable()` 惰性取用，因此**开关组合不会让启动失败**。
- `spring.autoconfigure.exclude` 排除了 `OllamaChatAutoConfiguration` 与 `OpenAiChatAutoConfiguration`，ChatModel 完全由 `ChatModelConfiguration` 手工构造（Embedding 仍走 `RagConfiguration.embeddingModel()` 手工构造）。
- `HybridRetrievalTool` / `VectorOnlyRetrievalTool` 刻意**不加** `@Component`，避免与条件 Bean 冲突。

---

## 3. 数据流

### 3.1 冷启动数据摄取链路

`RagDataLoader`（`ApplicationRunner`）在容器就绪后执行：

```
run(args)
 ├─ classpath:/data/beers.json.gz 是否存在？
 │    当前 src/main/resources/data 为空 → 走 downloadDataFile()
 │    FileDownloader: DATA_BEERS_JSON_GZ = https://arxiv.org/pdf/2506.10380
 │      · URL 含 arxiv.org → 扩展名强制 .pdf → Files.createTempFile("beers-", ".pdf")
 │      · connect 10s / read 60s，并临时 trust-all SSL SocketFactory
 ├─ checkDbCollectionRecords()：DocumentCountProvider.getDocumentCount(collection)
 │    MilvusDocumentCountProvider = flush → sleep(2s) → getCollectionStatistics → row_count
 │    row_count >= 10 → 直接 return（幂等，跳过摄取）
 ├─ readDocumentsFromResource(file)  ← 与运维重建共用，保证切块/ID 一致
 │    .docx → RAGFlowDocxParser.parse + convertSectionsToDocuments + 表格转 Document(type=table)
 │    .pdf  → DeepSeekOcrDocumentReader.read（见下）→ 切块算法二选一
 │    其他  → gz/zip 先解压 → JsonReader(resource, KEYS{name,abv,ibu,description})
 │    出口统一 TokenLimitEnforcer.enforceTokenLimit(...)
 ├─ vectorStore.add(documents)  → OllamaEmbeddingModel(bge-m3) 批量向量化 → Milvus upsert
 └─ hybridSearchService.indexForBM25(documents)  → Lucene 建索引 + Redis 持久化
```

PDF 的 OCR 子链路（`DeepSeekOcrDocumentReader`）：

```
PDF → PDFBox 逐页渲染（100 DPI, RGB）→ JPEG 压缩（quality 0.9）→ Base64
    → 4 线程并行调用 https://api.siliconflow.cn/v1/chat/completions
      model=deepseek-ai/DeepSeek-OCR，prompt="Convert the document to markdown."
      content=[image_url(data:image/jpeg;base64,...), text(OCR_PROMPT)]，max_tokens=4096
    → 按 pageIndex 排序合并（失败页插入 "[Page N OCR failed]"，页间 "\n\n---\n\n"）
    → MarkdownProcessor.processMarkdown 切块（chunkTokenNum/delimiter/separateTables）
      每个 chunk 写入 metadata: chunk_type / start_line / end_line
    → 同时把整篇 Markdown 落盘 ocr-output/<原名>.md（即仓库里 beers-*.md 的来源）
```

切块算法由 `chunking.algorithm` 二选一：`markdown`（当前默认，`MarkdownProcessor`）或 `token`（`TokenTextSplitter`，参数 `chunking.defaultChunkSize=1000`、`minChunkSizeChars=400`、`minChunkLengthToEmbed=5`、`maxNumChunks=10000`、`keepSeparator=true`，断句边界 `. 。 ! ？ ; \n`）。

**token 上限守卫**（架构上的关键防线）：`TokenCountBatchingStrategy` 用 jtokkit CL100K_BASE 计数，单文档阈值 `8191*(1-0.1)=7372`，超限会在向量化前直接抛 `IllegalArgumentException` 打断启动。`TokenLimitEnforcer` 以同一计数口径把超过 `SAFE_TOKEN_LIMIT=6000` 的文档再切一遍（优先落在 `\n!?;。；！？` 边界，退化时二分定位硬切），并给每片写 `split_total` / `split_part`。因此 `chunking.markdown.chunkTokenNum` 必须显著小于 7372（当前 800）。

### 3.2 Native 问答链路

```
前端 POST /chat/{chatId}  body {prompt}
  → RagController.chatMessage  立即返回 CompletableFuture（不占 Tomcat 线程）
    → CompletableFuture.supplyAsync(ragService.retrieve(chatId, prompt))
        RagService.retrieve(message):
          1. 检索模式选择：hybridSearchEnabled && hybridSearchService != null
               是 → hybridSearchService.hybridSearch(message, topk=10)
               否 → store.similaritySearch(topK=10)
          2. rerankEnabled && rerankService != null → rerank(message, docs, rerank.topN=5)
          3. SystemPromptTemplate(classpath:/prompts/system-qa.st).createMessage({documents})
             documents = 各 doc.getText() 以 \n 拼接（不带引用标记、不带 ID）
          4. chatModel.call(new Prompt([system, user])) → Generation
        saveTurns(chatId, question, answer)：仅当 answer 非空才写 user/assistant
    .orTimeout(65s)
    .thenApply(Generation → Message{message})
    .exceptionally(TimeoutException → "LLM 服务响应超时，请稍后重试" ;
                   其他 → "错误: " + msg)  → HTTP 500
```

特征：单轮、无规划、无引用校验，检索内容以纯文本拼进系统提示；失败/超时不写历史。

### 3.3 Agentic 问答链路

`AgentRuntimeService.run()` 是唯一的流程控制器，常量即硬边界：
`MAX_TOOL_CALLS=3`、`DEFAULT_TOP_K=5`、`MAX_TOP_K=20`、`MAX_CONTEXT_CHARS=12000`。

```
① 入参校验    question 为空 → 抛 IllegalArgumentException("question is required")
② traceId     UUID.randomUUID()（每次请求新生成，与 OTel traceId 无关联）
③ 读历史      sessionId != null → memory.recent(sessionId)
              ⚠ 当前仅用于日志，未拼入 prompt（MVP 刻意隔离未筛选的历史）
④ 规划        planner.plan(question) → QueryPlan
              needsClarification → 立即返回 {answer=澄清文案, evidence=[], plan=["clarify"], grounded=false}
⑤ topK 收敛   request.topK() == null ? 5 : clamp(1, .., 20)
⑥ 检索循环    queries = plan.retrievalQueries().limit(3)
              每个 query：steps += "hybrid_retrieve"
                retrievalTool.retrieve(query, topK) → List<Evidence> 累加
                抛异常 → logger.error + steps += "retrieval_failed"，链不中断
⑦ 上下文      contextBuilder.build(allEvidence, 12000) → Context(text, evidence)
⑧ 空证据兜底  evidence 为空 → answer="当前知识库中未检索到足够的相关资料，无法可靠回答。"
              写历史后返回 {evidence=[], grounded=false}
⑨ 生成        steps += "generate_answer" → answerGenerator.answer(question, context.text())
⑩ 引用校验    groundingChecker.hasValidCitations(answer, context.evidence())
              通过 → steps += "grounding_check_passed"
              不通过 → answer 替换为降级文案 + steps += "grounding_check_failed"
                     （MVP 只做透明降级，不做二次修正/重检索）
⑪ 落库        saveTurns(sessionId, question, answer)：sessionId 为空则跳过；写失败只 warn
⑫ 响应        AgentChatResponse(traceId, answer, evidence, plan, grounded)
```

规划规则（`RuleBasedQueryPlanner`）：空/空白 → 澄清；否则按 `并且|同时|另外|以及|；|\?` 切分、`trim`、过滤空白、`limit(3)`；切分为空则退回整句。

### 3.4 混合检索与 RRF 融合

```
hybridSearch(query, topK):
  retrieveK = max(topK * 2, 20)                    ← 两路各自多召回，供融合排序
  并行：CompletableFuture.supplyAsync(searchVector) + supplyAsync(searchBM25)
  vectorFuture.get() / bm25Future.get()（无显式超时）
     任一 get() 抛异常 → searchVectorOnly(query, topK)（整体退化为纯向量）
  rrfFusion(vectorResults, bm25Results, topK):
     RRF_score = Σ weight / (k + rank)
     dense:  weight = hybrid.search.dense.weight  = 0.7
     sparse: weight = hybrid.search.sparse.weight = 0.3
     k     : hybrid.search.rrf.k                 = 60
     按 docId 聚合 → 降序 → limit(topK)
     写回 metadata: fusion_score = totalScore, sources = ["vector","bm25"]
  回写 Document.metadata 后返回
```

单路细节：

- `searchVector`：`SearchRequest.builder().query().topK()`，rank 取返回列表下标 +1，score 用位置近似 `1.0 - i*0.01`（不是真实相似度）。异常 → `Collections.emptyList()`。
- `searchBM25`：`!bm25SearchService.isIndexBuilt()` 时**静默跳过**（只 WARN），异常同样返回空列表——这就是"BM25 悄悄退化成纯向量"现象的源头，需靠 `GET /admin/bm25/status` 定位。
- 两路 id 不一致时（例如 BM25 索引从 Redis 恢复后）会分裂成两个融合桶，导致同一文档分数被摊薄。

### 3.5 会话记忆链路

两条通道写入的是**同一个 Redis 结构**，因此同一会话 ID 来回切换历史不丢。

```
写：ConversationMemory.append(sessionId, role, content)
    RedisConversationMemory → LPUSH 语义（rightPush）rag:session:{sessionId}，值为 {"role","content"} JSON
    LTRIM 保留最后 12 条（MAX_MESSAGES=12）；EXPIRE 24h（TTL 每次写刷新）
    Jackson 3（tools.jackson）序列化；失败包 IllegalStateException
读：ConversationHistoryService.history(id)
    recent() 全量 range(0,-1) → 过滤空白 content → normalizeRole（非 "user" 一律视作 assistant）
    → ConversationHistoryResponse{sessionId, messages[]}
    Redis 异常 → IllegalStateException → Controller 转 500 + {message}
```

边界约定（代码注释明确）：记忆层**只持久化 role/content**，`traceId`、`evidence`、`plan`、`grounded` 不落库，因此历史接口不伪造元信息字段；工具轨迹/证据与对话记忆分离，`AgentRuntimeService` 读历史但不把历史当证据。

`RedisConversationMemory` 自身声明了安全缺口：key 仅含 sessionId，未绑定租户/用户，生产前必须改为 `rag:session:{tenantId}:{userId}:{sessionId}` 并校验调用者权限。

### 3.6 BM25 持久化与运维链路

```
写入（indexDocumentsInternal(persistToRedis=true)）
  saveDocuments(docs)：先 getDocumentCount()，>0 则跳过（防重复固化）
    每条 → bm25:doc:{docId} = {"id","text"}（text 截断到 2000 字符 + "..."）
         → SADD bm25:index:docs {docId}
    命中 OOM（异常链含 "OOM"）→ redisAvailable=false，本进程内永久停用持久化
恢复（Bean 创建时）
  bm25SearchService Bean → setPersistenceService(...) → restoreFromPersistence()
    hasPersistedDocuments() → loadDocuments() → indexDocumentsInternal(docs, false)
  indexDocuments(docs) 检查 Redis 已有数据则跳过重复建索引
运维
  GET  /admin/bm25/status → Bm25StatusResponse
      聚合 memCount / redisCount / milvusCount + indexBuilt + redisAvailable
      healthy = indexBuilt && memCount > 0
      consistent = healthy && milvusCount > 0 && |memCount - milvusCount| <= max(1, milvusCount/20)
      recommendation 给出下一步动作文案（含指向 /admin/bm25/rebuild）
  POST /admin/bm25/rebuild → RagDataLoader.rebuildBm25FromSource()
      clearBM25Index()（内存 + Redis 全清，避免拒绝覆盖）→ loadSourceDocuments()
      → indexForBM25 → 返回 Bm25RebuildResponse；失败转 500（不抛栈到 HTTP 层）
```

### 3.7 评测数据流

**Python 侧（`DeepEvalSpringAIRAG/`）**——两种入口并存：

```
src/main.py（结构化实现）
  get_config()（SILICONFLOW_KEY 必需）
  → SiliconFlowLLM(Qwen/Qwen2.5-7B-Instruct) + SiliconFlowBGEEmbedding(BAAI/bge-large-en-v1.5)
  → MilvusService.fetch_docs(limit=50)：MilvusClient.query(filter='doc_id != ""',
        output_fields=["content","doc_id"]) → {doc_id: content}
  → Evaluator.generate_goldens：随机采样 25 个 context，Synthesizer
        .generate_goldens_from_contexts(max_goldens_per_context=1, include_expected_output=True)
        → 截断至 25；优先从 rag_test_dataset.json 缓存加载
  → Evaluator.save_dataset → rag_test_dataset.json
  → Evaluator.run_evaluation：逐条 POST /retrieve {query, topK}
        → LLMTestCase(input, actual_output=" ".join(retrieved), expected_output, retrieval_context)
        → ContextualRecallMetric(threshold=0.7) → evaluate()
        → evaluation_report.json {total_test_cases, average_context_recall,
                                  average_id_hit_rate, individual_results[]}
1_generate_testset.py：同流程的单文件早期版本（硬编码 MILVUS_URI=10.1.1.234）

src/agent_client.py（端到端 Agent 评估入口）
  run_agent(base_url, session_id, question, top_k=8) → POST /api/agent/chat（timeout 90s）
  → {input, actual_output=answer, retrieval_context=[evidence.content],
     evidence_ids=[evidence.documentId], trace_id, plan, grounded}
  用途分层：检索质量 ContextualRecall/Precision、答案质量 Faithfulness/Correctness、
            引用质量 grounding、Agent 执行质量（任务完成度、重复检索、降级行为）
```

**Java 侧（LLM-as-a-Judge 回归）**：`FaithfulnessEvaluationTest` / `CorrectnessEvaluationTest` / `RagEvaluationTest` 三个 `@SpringBootTest`，均由 `@EnabledIfEnvironmentVariable(named="SILICONFLOW_KEY")` 门控、加载 `application-evaluation.properties`（judge 模型 `THUDM/GLM-Z1-9B-0414`，阈值 relevance 0.7 / factuality 0.8），逐条读 `src/test/resources/test-data/*.json` 打分，断言"至少 70% 判定正确"。`FaithfulnessEvaluationTest` 还包含区分能力测试（同一 case 的 faithful 分数必须 > unfaithful）。

### 3.8 超时层级（跨层约束，改动前必读）

```
上游 HTTP 读取 60s  <  RagController orTimeout 65s  <  spring.mvc.async.request-timeout 90s
（SimpleClientHttpRequestFactory / OpenAiChatOptions.timeout）
```

任一层错位都会破坏错误可读性：容器超时先到 → 对外 503（`AsyncRequestTimeoutException`），业务层友好文案永远回不到前端；HTTP 读取无界 → 线程长时间阻塞。

不在该层级内的独立预算：

> 注意：上面的 65s / 90s 两级只作用于 Native 的异步链路。`/api/agent/chat` 是同步端点，一轮可能发起 3 次检索 + 1 次生成 + 1 次 rerank，实际只受上游 HTTP 60s 读超时约束（DeepEval `agent_client.py` 因此把客户端超时设为 90s）。若要给 Agent 链路一个与 Native 对等的业务超时层，需要把它也改成 `CompletableFuture` 入口。

| 位置 | 连接 | 读取 | 理由 |
|------|------|------|------|
| `RagConfiguration.restClientCustomizer` | 10s | 60s | 聊天路径上游默认超时 |
| `ChatModelConfiguration.openAiChatModel` | — | 60s（`spring.ai.openai.timeout`） | Spring AI 2.1.0 改用官方 SDK，不走容器 RestClient，必须显式设 |
| `RagConfiguration.embeddingModel`（Ollama） | 10s | **300s** | 本地 bge-m3 批量 embed 实测 40s+；不在聊天路径，故放宽 |
| `OllamaApi`（chat） | 60s | 60s | `spring.ai.ollama.*-timeout` |
| `RerankService` | 10s | 20s | 失败即降级为原序，快速失败 |
| `DeepSeekOcrDocumentReader` | 30s | 120s | 单页 OCR，摄取期，非请求路径 |
| `MilvusServiceClient` | 30s | keep-alive/idle 30s | 端口在代码内固定 19530 |
| `FileDownloader` | 10s | 60s | 数据源下载 |

---

## 4. 数据结构

### 4.1 HTTP 层 DTO（除 `RagController.Message`/`Prompt` 外均为 record）

| 类型 | 字段（顺序即 JSON 顺序） | 契约要点 |
|------|--------------------------|----------|
| `AgentChatRequest` | `sessionId, question, topK(Integer)` | `topK` 可空；Runtime 收敛到 1..20 |
| `AgentChatResponse` | `traceId, answer, evidence(List)`, `plan(List<String>)`, `grounded(boolean)` | `plan` 是执行轨迹；`evidence` 只含**实际进入上下文**的证据 |
| `Evidence` | `documentId, content, source, page(Integer), retrievalSource, score(Double)` | `score` 可为 null；RRF/rerank 分不代表模型置信度 |
| `RetrievalResult` | `id, content, score(Double), metadata(Map)` | 评测专用（`POST /retrieve`），由 `RagService.mapToResult` 从 metadata `distance` 取分 |
| `ChatSession` | `chatId` | 字段名必须叫 `chatId`（前端按此读取），不能复用 `message` |
| `ConversationHistoryResponse` | `sessionId, messages(List<HistoryMessage>)`；`HistoryMessage(role, content)` | 会话不存在/无历史 → 空列表 + 200 |
| `Bm25StatusResponse` | `hybridEnabled, indexBuilt, memCount, redisConfigured, redisAvailable, redisCount, milvusCount, healthy, consistentWithMilvus, recommendation` | milvus 计数失败为 -1 |
| `Bm25RebuildResponse` | `success(boolean), bm25DocumentCount(Integer), message, error` + 静态工厂 `success(count)` / `failure(error)` | `count==0` 时 message 为 `No documents loaded from data source`；failure → HTTP 500 |
| `RagController.Message` / `RagController.Prompt` | `message` / `prompt` | 内部静态类（POJO，非 record），Native 通道沿用 |

`plan` 字段取值集合：`clarify`、`hybrid_retrieve`、`retrieval_failed`、`generate_answer`、`grounding_check_passed`、`grounding_check_failed`。

### 4.2 内部数据结构

| 类型 | 定义位置 | 结构 |
|------|----------|------|
| `QueryPlanner.QueryPlan` | `agent/QueryPlanner.java` | `(List<String> retrievalQueries, boolean needsClarification, String clarificationQuestion)` |
| `ContextBuilder.Context` | `agent/ContextBuilder.java` | `(String text, List<Evidence> evidence)`；text 块格式 `[证据 {id} | 来源={source} | 页码={page}]\n{content}` |
| `VectorOnlyRetrievalTool.RerankHolder` | 同名文件 | `record RerankHolder(RerankService)` + `available()`，用于无 Bean 时传 null |
| `HybridSearchService.RankedDoc` | 私有静态类 | `(String id, Document document, float score, int rank)` |
| `HybridSearchService.FusionScore` | 私有静态类 | `(Document, double totalScore, List<String> sources)` |
| `BM25SearchService.ScoredDocument` | 公开静态类 | `(Document document, float score, int rank)` |
| `RerankResult` | `service/RerankResult.java` | **class**（非 record），`(Document, double relevanceScore)`，`compareTo` 按分数降序 |
| `TextChunk` | `util/TextChunk.java` | Lombok `@Data` class：`content, type, startLine, endLine, associatedImages(List)`；构造器只填前 4 项 |
| `MarkdownElement` | `util/MarkdownElement.java` | class，构造器顺序为 `(type, content, startLine, endLine)` |
| `ProcessingResult` | `util/` | `(List<TextChunk> chunks, List<String> tables)` |
| `TableExtractionResult` | `util/` | `(String remainder, List<String> tables)` —— 移除表格后的正文 + 抽出的表格 |

### 4.3 Spring AI `Document` metadata 字典

这是系统里**唯一的事实传递介质**：跨层信息全靠 metadata 键名对齐，任一端改名即静默丢失。

| key | 写入端 | 读取端 | 说明 |
|-----|--------|--------|------|
| `source` | OCR（原始文件名）、JSON reader | `EvidenceMapper` → `Evidence.source` | 缺失 → `unknown` |
| `page` | 未见生产写入端（预留） | `EvidenceMapper` → `Evidence.page` | 缺失 → `unknown` |
| `retrievalSource` | 预留（dense/bm25/hybrid） | `EvidenceMapper` → `Evidence.retrievalSource` | 当前多为空 |
| `fusion_score` | `HybridSearchService.rrfFusion` | `EvidenceMapper` 分数候选第 4 位 | RRF 总分 |
| `sources` | `rrfFusion`（`["vector","bm25"]`） | 调试/日志 | 命中通道 |
| `rerank_score` / `score` / `distance` | Spring AI VectorStore / rerank（当前未显式写 `rerank_score`） | `EvidenceMapper` 分数候选 1..3 位 | 按此顺序取首个可解析值 |
| `chunk_type` / `start_line` / `end_line` | `HybridDocumentService`（Markdown 切块） | 检索结果透传 | 表格块 type=`table` |
| `split_total` / `split_part` | `TokenLimitEnforcer` 二次切分 | 溯源用 | 1-based |
| `type` | `pdf-ocr` / `table` | 日志 | — |
| `ocr_model` | `DeepSeekOcrDocumentReader` | 日志 | — |

`EvidenceMapper` 的分数取值顺序：`rerank_score` → `score` → `distance` → `fusion_score`，逐个尝试 `Number` 或字符串解析，全部失败则 `score=null`。

### 4.4 存储层结构

**Milvus collection**（`MilvusVectorStore.builder` 在 `RagConfiguration.milvusVectorStore` 中显式构造）：

| 参数 | 代码实际值 | 配置来源 |
|------|-----------|----------|
| collectionName | `paper_collection_vpdf` | `spring.ai.vectorstore.milvus.collection-name` |
| databaseName | `"default"` | 硬编码 |
| indexType | `IVF_FLAT` | 硬编码（`spring.ai.vectorstore.milvus.index-type` 未被读取） |
| metricType | **`COSINE`** | 硬编码（配置里的 `metric-type=IP` 不生效，见第 8 节） |
| embeddingDimension | `1024` | `spring.ai.vectorstore.milvus.embeddingDimension` |
| batchingStrategy | `TokenCountBatchingStrategy` | 硬编码（阈值 7372） |
| initializeSchema | `true` | 硬编码 |
| host / port | host 读 `...milvus.client.host`；**port 固定 19530** | 配置的 `client.port` 未被读取 |

Spring AI 默认列布局：`id`、`content`、`metadata`、`embedding`；评测脚本另按 `doc_id` / `content` 查询（`filter='doc_id != ""'`），说明历史数据集合曾带 `doc_id` 列 —— 该口径与 Java 侧 `id` 列并不完全一致，是评测集为空的已知风险点。

**Lucene 索引**（`ByteBuffersDirectory`，纯内存）：

| 字段 | 类型 | 存储 | 说明 |
|------|------|------|------|
| `id` | `StringField` | YES | 回查 `documentStore` 的键 |
| `content` | `TextField` | NO | 唯一检索域，`StandardAnalyzer`；相似性 `BM25Similarity(k1=1.2, b=0.75)` |

外加一个与索引并行的 `Map<String, Document> documentStore`（HashMap，配合 `ReentrantReadWriteLock`）保存原始 Spring AI `Document`。

**Redis key 空间**：

| Key | 类型 | 值 | 生命周期 |
|-----|------|----|----------|
| `rag:session:{sessionId}` | List | 每元素 `{"role","content"}` JSON | 裁剪至 12 条，TTL 24h（写刷新） |
| `bm25:doc:{docId}` | String | `{"id","text"}`，text 截断 2000 字符 | 永久（无 TTL），`clearDocuments` 删除 |
| `bm25:index:docs` | Set | docId 集合 | 永久；计数即 `SCARD` |

**其他数据面**：`src/main/resources/prompts/system-qa.st`（Native 系统提示，占位符 `{documents}`）、`ocr-output/*.md`（OCR 落盘）、`frontend/build` → `target/classes/static`（构建期拷贝）。

### 4.5 评测数据集结构

```json
// src/test/resources/test-data/faithfulness-test-cases.json
{ "description": "...", "testCases": [ {
  "id": "...", "question": "...", "context": "...",
  "faithfulAnswer": "...", "unfaithfulAnswer": "...", "scenario": "Concept Definition"
} ] }

// correctness-test-cases.json：id / question / context / expectedAnswer / scenario
// rag-test-cases.json：      id / question / expectedContext / expectedAnswer / scenario
// DeepEvalSpringAIRAG/rag_test_dataset.json：DeepEval Golden（input / expected_output / context）
```

三个测试集都围绕 TableRAG 论文（arXiv:2506.10380）构造，与 3.1 的数据源同一篇论文，形成"摄取语料 == 评测语料"的闭环。

---

## 5. 前后端交互逻辑

### 5.1 REST 端点总表

| 方法 + 路径 | 控制器 | 请求体 | 响应 | 特性 |
|-------------|--------|--------|------|------|
| `POST /chat/startChat` | RagController | — | `{chatId}` | 仅生成 UUID，无状态 |
| `POST /chat/{chatId}` | RagController | `{prompt}` | `{message}` | **异步** `CompletableFuture`，65s `orTimeout`，超时/异常 → 500 + 可读文案 |
| `GET /chat/history/{chatId}` | RagController | — | `ConversationHistoryResponse` | 同步；空历史 200；Redis 失败 500 `{message}` |
| `POST /api/agent/chat` | AgentChatController | `AgentChatRequest` | `AgentChatResponse` | **同步**返回（不包 `CompletableFuture`），因此不受 `spring.mvc.async.request-timeout` 约束，只有上游 HTTP 读超时兜底 |
| `GET /api/agent/history/{sessionId}` | AgentChatController | — | `ConversationHistoryResponse` | 与 native 同构、共用服务 |
| `POST /retrieve` | RagController | `{query, topK}`（字符串） | `[RetrievalResult]` | Python 评测专用；纯向量；`topK` 缺省 5 |
| `GET /admin/bm25/status` | RagController | — | `Bm25StatusResponse` | 运维自检 |
| `POST /admin/bm25/rebuild` | RagController | — | `Bm25RebuildResponse` | 失败 → 500 |
| `POST /documents/upload` | RagController | `doc` | `"Document upload not supported"` | **占位实现**，未接通 |
| `GET /actuator/{health,info,metrics,prometheus,loggers}` | Boot | — | — | 仅暴露这五项 |

### 5.2 前端路由与会话生命周期

前端刻意**不引入 react-router**，用 hash 路由（`App.js`）：产物由 Spring Boot 静态目录托管，`#/native` 无需服务端 forward 配置。

```
window.location.hash
  ''（其他）      → <EntryPage/>       两张卡片：Native(legacy) / Agentic(beta)
  '#/native'      → <ChatWindow key="native"  mode="native"/>
  '#/agentic'     → <ChatWindow key="agentic" mode="agentic"/>
```

`key={mode}` 保证切通道时组件重挂载，各自申请独立会话，不把另一通道的消息列表带过来。

`ChatWindow` 会话流程：

```
componentDidMount → start()
  acquireSession(forceNew)
    localStorage['rag:native-session-id' | 'rag:agentic-session-id'] 有值 → 复用
    否则 agentic: StartAgentSession()（内部同样打 /chat/startChat）
           native : (await StartChat()).chatId
           → 写入 localStorage
  setState(chatId, awaitingServer=false) → loadHistory(chatId)
    FetchAgentHistory / FetchNativeHistory → 恢复 messages（仅文本，无 meta）
    activeSessionId 守卫：加载期间点了"新会话"则丢弃结果
  失败 → awaitingServer=true + setTimeout(start, 5000) 无限重试
        （刻意不在前端造 UUID 兜底，避免产生服务端查不到历史的孤儿会话）
```

发送闸门 `uiDisabled()`：`messagePending || historyLoading` 期间禁止发送与"新会话"，防止 Agentic 单轮数十秒内并发打出多条检索请求，也防止新消息排在未加载完的历史之前。

### 5.3 通信契约与错误处理约定

`api.js` 是唯一的后端出口，两条通道风格不同：

| 维度 | Native（`SendMessage`） | Agentic（`SendAgentMessage`） |
|------|--------------------------|-------------------------------|
| URL | `chat/{chatId}` | `api/agent/chat` |
| 请求体 | `{prompt}` | `{sessionId, question, topK?}` |
| 失败处理 | 直接抛出，由 `ChatWindow` try/catch 兜住 | **不抛异常**，返回 `{ok:false, status, message, error}` |
| 响应字段 | `{message}` | `{traceId, answer, evidence, plan, grounded}` + 前端补齐 `message=answer` |
| 取消 | 不支持 | 支持 `options.signal`（可传 `AbortSignal.timeout(ms)`） |
| 空问题 | 交后端处理 | 前端先拦（后端会抛 `IllegalArgumentException`） |

错误文案回退链：`data.message` → `data.error` → `Agent 请求失败（HTTP {status}）`；网络异常/取消分别映射为"无法连接 Agent 服务：…"与"请求已取消。"。`safeJson` 保证非 JSON 响应体不炸解析。

历史接口同样"失败不抛出"：返回 `{ok, status, messages, error}`，非法响应体一律当空历史，页面继续可用；前端再做一次 role 归一化（非 `user` 归 `assistant`），与后端 `ConversationHistoryService.normalizeRole` 一致。

### 5.4 证据面板渲染规则

只有 `response.ok === true` 时才把 `meta`（traceId/grounded/evidence/plan）挂到消息上。原因写在代码注释里：请求失败时 `grounded` 恒为 `false`、`evidence` 恒为空，无条件渲染会把一条 503 气泡说成"引用校验未通过"，误导为模型答案不可信。

面板结构（`<details>` 折叠）：摘要单行 `引用校验通过/未通过 · 证据 N 条 · traceId …`；展开后 `步骤：plan.join(' → ')`，每条证据一行 `[i] source|documentId · 第X页 · retrievalSource · score 0.xxxx`，正文截断 300 字符。

### 5.5 构建期耦合

```
pom.xml 属性：node.version=v18.16.0、npm.version=9.5.1、frontend-maven-plugin.version=1.15.0
实际启用插件：maven-resources-plugin（process-classes 阶段 frontend/build → target/classes/static）
未启用：frontend-maven-plugin（只有版本属性，没有 <plugin> 声明）
开发期：frontend/package.json "proxy": "http://localhost:8080"
```

因此前端产物必须手工生成：`cd frontend; npm ci; npm run build`，再 `mvn package`；`frontend/build` 不存在时该拷贝阶段静默复制 0 个文件（迁移记录 ENV-5 已记录此现象）。

---

## 6. 配置体系

`application.properties` 按职责分组（全部键均已核对；`src/main/resources/data` 为空这一点在 3.1 已说明）：

| 分组 | 关键配置 |
|------|----------|
| Bean 覆盖与自动配置 | `spring.main.allow-bean-definition-overriding=true`、`spring.autoconfigure.exclude=`（排除 Ollama/OpenAI Chat 自动配置） |
| 嵌入模型 | `spring.ai.ollama.base-url=http://127.0.0.1:11434`、`...embedding.options.model=bge-m3:latest`、`...embedding.connect-timeout=10s`、`...embedding.read-timeout=300s` |
| Ollama 聊天 | `spring.ai.ollama.chat.options.model=qwen2.5:0.5b`、`connection-timeout=60s`、`read-timeout=60s` |
| 聊天供应商 | `chat.provider=openai`（注释写"默认 ollama"，实际配置值是 openai） |
| OpenAI/SiliconFlow | `spring.ai.openai.api-key=${SILICONFLOW_KEY:}`、`base-url=https://api.siliconflow.cn`、`chat.options.model=Qwen/Qwen3-8B`、`temperature=0.7` |
| 向量库 | `spring.ai.vectorstore.milvus.client.host=192.168.0.223`、`collection-name=paper_collection_vpdf`、`embeddingDimension=1024`（另有 `index-type`/`metric-type`/`port`/`database-name`/`consistency-level` 当前未被代码读取） |
| 遗留 Pinecone 键 | `spring.ai.vectorstore.pinecone.dimension=1024`、`index-name=paper_collection_vpdf`（被 `RagConfiguration` 当作索引名/维度来源） |
| 召回与精排 | `topk=10`、`rerank.enabled=true`、`rerank.model=BAAI/bge-reranker-v2-m3`、`rerank.topN=5` |
| 混合检索 | `hybrid.search.enabled=true`、`rrf.k=60`、`dense.weight=0.7`、`sparse.weight=0.3`、`collection-name` |
| 切块 | `chunking.algorithm=markdown`、`chunking.markdown.chunkTokenNum=800`、`delimiter=\n!?;\u3002\uFF1B\uFF01\uFF1F`、`separateTables=true`；token 算法参数 `defaultChunkSize=1000` 等 |
| OCR | `deepseek-ocr.enabled=true`、`deepseek-ocr.model=deepseek-ai/DeepSeek-OCR` |
| Redis | `spring.data.redis.host=${REDIS_HOST:localhost}`、`port=${REDIS_PORT:6379}`、`password=${REDIS_PASSWORD:}`、`ssl.enabled=false` |
| 异步超时 | `spring.mvc.async.request-timeout=90000` |
| 可观测性 | `spring.ai.chat.observations.log-prompt/completion=true`、`vectorstore.observations.log-query-response=true`、`management.endpoints.web.exposure.include=health,info,metrics,prometheus,loggers`、`management.prometheus.metrics.export.enabled=true`、`management.metrics.tags.application=spring-ai-rag-demo`、`percentiles-histogram.http.server.requests=true`、`management.tracing.sampling.probability=1.0` |
| 日志 | `logging.level.com.redis.demo.spring.ai=DEBUG`、`org.springframework.ai=DEBUG` |
| SSL | `permissive.ssl=true`、`javax.net.ssl.trustStore*=`（空） |

环境变量集合：`SILICONFLOW_KEY`（聊天/OCR/rerank/评测共用，必需）、`REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`、容器内 `MILVUS_HOST`、`SPRING_AI_VECTORSTORE_MILVUS_CLIENT_HOST`、`SPRING_AI_VECTORSTORE_REDIS_URI`、`SPRING_AI_AZURE_OPENAI_*`、以及 Python 侧同名 `SILICONFLOW_KEY`。

API Key 优先级（`ChatModelConfiguration` 嵌套占位符）：`SILICONFLOW_KEY` → `spring.ai.openai.api-key` → `OPENAI_API_KEY`；`HybridDocumentService` 构造里还有一段显式回退到 `System.getenv("SILICONFLOW_KEY")` 的兜底。这是历史上 401 问题（key 与 base-url 服务不匹配）的根因位置，改动时必须保证 key 与 `base-url` 同源。

测试 profile：`application-test.yml`（`test-key`、localhost Redis）、`application-evaluation.properties`（judge 模型与阈值）、`testcontainers.properties`。

---

## 7. 可观测性与测试体系

**可观测性**：Actuator + Prometheus 导出 + Micrometer Tracing（OTel bridge，采样率 1.0）。两个 ChatModel Bean 均显式注入容器内 `ObservationRegistry`（`getIfUnique(() -> NOOP)`），使 prompt/completion 观测生效；`embeddingModel` 用的是 `ObservationRegistry.create()`（独立注册表，不接容器，嵌入调用不进主链路指标）。`AgentChatResponse.traceId` 是应用自生成的 UUID，与 OTel traceId 目前**没有打通**。

**测试分层**（`pom.xml` 中 Surefire 只收 `**/*Test.java`，Failsafe 收 `**/*IT.java` 与 `**/*IntegrationTest.java`）：

| 层 | 代表 | 依赖 |
|----|------|------|
| 纯单测 | `HybridSearchServiceTest`、`BM25SearchServiceTest`、`TokenLimitEnforcerTest`、`MarkdownElementExtractorTest`、`SectionToDocumentConverterTest`、`RAGFlowDocxParserTests`、`ConversationHistoryServiceTest` | 无外部服务 |
| Web 切片 | `RagControllerTest`（`@WebMvcTest`，Boot 4.2 需显式引入 `spring-boot-starter-webmvc-test`） | Mock MVC |
| 服务级 | `RagServiceTest`、`RagServiceNativeHistoryTest`、`AgentRuntimeServiceTest`、`RerankServiceTest`、`RagDataLoaderTest`、`DeepSeekOcrDocumentReaderTest` | Mock 协作对象 |
| 集成 | `RagDataLoaderIntegrationTest`（`@Testcontainers`）、`Bm25IndexRecoveryIntegrationTest` | Docker/Milvus |
| LLM-as-a-Judge | `FaithfulnessEvaluationTest`、`CorrectnessEvaluationTest`、`RagEvaluationTest` | `SILICONFLOW_KEY` 存在才启用 |
| Python | `DeepEvalSpringAIRAG/tests/`（`pytest.ini` 配置，mock 掉外部依赖） | pytest |
| 前端 | `App.test.js`（CRA + Testing Library） | jest |

---

## 8. 已知限制与技术债

以下每条都能在源码中定位，按影响面排序：

1. **Redis 恢复丢失文档身份**：`BM25DocumentPersistenceService.loadDocuments()` 用 `new Document(text, new HashMap<>())` 重建，既不带原 `docId` 也不带 metadata。后果：① 重启后 BM25 侧 id 与 Milvus 侧 id 不一致，RRF 按 id 聚合会把同一文档拆成两桶、融合分被摊薄；② `Evidence.source/page` 退化为 `unknown`，引用面板与 grounding 可读性下降。这是最值得优先修的结构性问题。
2. **混合检索并行执行使用公共池且 `get()` 无超时**：`CompletableFuture.supplyAsync` 走 `ForkJoinPool.commonPool()`，`vectorFuture.get()` 不带 timeout；向量库卡住时会一直占用直到外层 65s/90s 兜住。`retrieveK=max(topK*2,20)` 也放大了单次召回延迟。
3. **BM25 静默退化**：`isIndexBuilt()` 为 false 或检索异常时只 WARN/ERROR 并返回空列表，对外表现与"纯向量"完全一致；只能靠 `GET /admin/bm25/status` 发现。
4. **Agent 步骤名与实现不符**：`steps.add("hybrid_retrieve")` 是硬编码，即使实际装配的是 `VectorOnlyRetrievalTool`（`hybrid.search.enabled=false`）也仍记作 hybrid_retrieve，评测与排障时会误读通道。
5. **历史未参与生成**：`AgentRuntimeService` 读取 `memory.recent()` 只打日志；`ConversationHistoryResponse` 也不含 evidence/traceId/plan。多轮上下文能力目前对用户不可见。
6. **上下文预算按字符不按 token**：`MAX_CONTEXT_CHARS=12000`，与模型 token 窗口无换算关系；`ContextBuilder` 超限即 `break`（后续证据整批丢弃），不做截断或按相关性回填。
7. **GroundingChecker 只校验引用 ID 合法性**，不校验语义支撑关系；且模型若完全不引用则判 `grounded=false`（`foundCitation` 要求至少一个引用）。真正的忠实性依赖第 7 节的离线评测。
8. **配置与代码不一致**：`metric-type=IP`、`index-type`、`client.port`、`database-name`、`consistency-level` 均未被 `RagConfiguration` 读取，实际生效的是硬编码 `COSINE`/`IVF_FLAT`/`19530`/`default`；`spring.ai.vectorstore.pinecone.*` 被当作 Milvus 索引名/维度来源（缺键会启动失败）；README 描述的 frontend-maven-plugin 未真正启用。
9. **评测链路口径漂移**：`evaluator.run_evaluation` 把检索内容拼接当 `actual_output`（不是端到端答案），且 `evaluate_id_hit_rate([], ...)` 恒返回 0（`agent_client.py` 注释已点明该缺陷但未回灌到 evaluator）；当前 `evaluation_report.json` 的 goldens 混入了 judge 提示文本、分数 0.0。`config.py` 默认 `MILVUS_URI=10.1.1.234` 与 `application.properties` 的 `192.168.0.223` 也不同。
10. **占位/死代码**：`POST /documents/upload` 固定返回不支持文案；`frontend/src/Services/ChatService.js` 请求 `/chat`（无 chatId）且无人引用。
11. **部署脚本假设 Maven wrapper**：`Dockerfile` 执行 `./mvnw verify`，但仓库根目录没有 `mvnw`；同时容器基础镜像 JDK 21 与 `pom.xml` 的 `java.version=17` 并存（编译目标 17、运行 21）。
12. **`application.properties` 编码不一致**：文件内多条中文注释呈双重编码乱码（与 `.properties` 按 ISO-8859-1 读取的规则混写），新增中文配置值仍需用 `\uXXXX` 转义（如 `chunking.markdown.delimiter`）。
13. **安全**：`SSLUtil.trustAllCertificates()` + `permissive.ssl=true` + `FileDownloader` 内 trust-all TrustManager/HostnameVerifier，仅限开发；`rag:session:{sessionId}` 未绑定租户/用户，存在跨会话读取风险（代码注释已自述）。

---

## 9. 技术选型与版本

| 层 | 选型 | 版本 | 备注 |
|----|------|------|------|
| 后端框架 | Spring Boot | 4.2.0 | 最低 Maven 3.6.3+；`@WebMvcTest` 拆到 `spring-boot-starter-webmvc-test` |
| AI 框架 | Spring AI / BOM | 2.1.0 | OpenAI 集成改为官方 SDK，`OpenAiApi` 移除 |
| 向量库 | Milvus SDK + starter | 2.6.21（随 starter 传递） | 服务端单机版 `milvusdb/milvus:v2.6.7` |
| 全文检索 | Apache Lucene | 9.11.1 | core / queryparser / analysis-common |
| 索引持久化与会话记忆 | Spring Data Redis | 由 Boot BOM 管理 | `StringRedisTemplate`，Redis Stack 容器或 Redis Cloud |
| 序列化 | Jackson 3 | 由 Boot 管理 | 包名 `tools.jackson.*` |
| 嵌入 | Ollama `bge-m3:latest` | 1024 维 | 本地，读超时 300s |
| 生成 | SiliconFlow `Qwen/Qwen3-8B`（`chat.provider=openai`）；或 Ollama `qwen2.5:0.5b` | — | 由 `chat.provider` 二选一 |
| 精排 | SiliconFlow `BAAI/bge-reranker-v2-m3` | — | `rerank.enabled` 门控 |
| OCR | SiliconFlow `deepseek-ai/DeepSeek-OCR` | — | 配合 PDFBox 渲染 |
| 文档解析 | Apache POI 5.2.3、pdfbox（随 starter）、Tika、commonmark 0.27.0 | — | DOCX/PDF/Markdown |
| Token 计数 | jtokkit（`JTokkitTokenCountEstimator`, CL100K_BASE） | 随 Spring AI | 与 batching 同口径 |
| 可观测 | Micrometer + Prometheus + OTel bridge | 随 Boot | 采样率 1.0 |
| 前端 | React 18.2.0 / React DOM 18.2.0 / React Bootstrap 2.9.2 / Bootstrap 5.3.2 / react-scripts 5.0.1 | — | CRA，hash 路由，无 react-router |
| 构建 | Maven、JaCoCo 0.8.16、Surefire/Failsafe 3.6.1、maven-resources-plugin | — | Testcontainers 1.21.4 需显式钉版（Boot BOM 不再管理） |
| 评测 | DeepEval ≥ 2.0、pymilvus ≥ 2.3、openai SDK ≥ 1.0、pytest ≥ 7 | — | Python ≥ 3.9 |
| 部署 | Docker + Docker Compose（etcd v3.5.5 + MinIO + Milvus standalone + redis-stack + 应用） | — | 详见 [DOCKER-README.md](DOCKER-README.md) |

选型对比：

| 技术点 | 选型 | 替代方案 | 选型理由 |
|--------|------|----------|----------|
| Agent 控制流 | Java 有界 Runtime（固定工具次数/预算） | 自由 ReAct 循环、模型自主 tool-calling | 行为可复现、成本可控、可结构化降级 |
| 检索规划 | 规则 `RuleBasedQueryPlanner` | LLM 规划器（schema 约束） | MVP 保证确定性；接口已预留替换点 |
| 融合算法 | RRF（加权，k=60，0.7/0.3） | 分数归一化线性混合 | 无需跨通道分数对齐，对量纲不敏感 |
| 全文索引 | Lucene 内存索引 + Redis 持久化 | Elasticsearch / OpenSearch | 零外部依赖，重启靠 Redis 恢复 |
| 引用校验 | 正则回查 ID 合法性 | 全量 LLM judge | 在线零成本拦截"编造引用"；忠实性交给离线评测 |
| 前端路由 | hash 路由 | react-router | 静态目录托管无需服务端 forward |
| 评测 | DeepEval（Python）+ LLM-as-a-Judge（Java）双轨 | 单一框架 | Python 侧覆盖检索指标，Java 侧可进 CI 回归 |

---

## 10. 演进路线

按"修一处解锁一处"的顺序建议：

1. **修数据身份链路**：`BM25DocumentPersistenceService` 持久化并恢复 `docId` + metadata，使 RRF 融合、`Evidence.source/page`、引用面板在重启后依然可信。这是当前性价比最高的一步。
2. **补检索元信息回写**：`HybridSearchService` / `RerankService` 显式写 `retrievalSource`、`rerank_score`，让 `EvidenceMapper` 不再依赖猜测键序；同时把 `AgentRuntimeService` 的步骤名改为按实际工具实现上报。
3. **给融合加超时与预算**：并行两路使用独立线程池 + `get(timeout)`，`ContextBuilder` 改 token 预算并支持截断/按相关性回填。
4. **启用历史**：引入带上下文预算与冲突处理的历史拼装（`ConversationContextBuilder`），并把 `traceId/evidence/plan` 一并落库，让 `GET /api/agent/history` 可回放。
5. **升级规划器**：在 schema 约束下替换为 LLM 规划器（仍由 Java 控制检索调用），并把 `RuleBasedQueryPlanner` 保留为兜底。
6. **收敛配置与代码**：让 `metric-type`/`index-type`/`port` 真正读取配置（或删掉无效键），移除 `pinecone.*` 遗留耦合，README 与 `pom.xml` 对齐前端构建方式，补 `mvnw`。
7. **评测口径统一**：以 `agent_client.run_agent` 为主入口评端到端答案质量，`/retrieve` 只用于检索指标；为 ID 命中率提供人工确认的 golden expected ids；把 DeepEval 与 Java judge 的模型/阈值统一到同一份配置。
8. **安全边界**：会话 key 绑定租户/用户并校验权限；关闭 trust-all SSL（改为可控 trustStore）；`/admin/*` 加鉴权。

---

## 总结

项目已从"单一 RAG 演示"演进为**双通道可控 RAG 平台**：底层由 `Milvus（语义）+ Lucene BM25（关键词）+ RRF + SiliconFlow Rerank` 提供检索能力，上层由 `RagService`（一次生成）与 `AgentRuntimeService`（有界 Agent：规划 → 至多 3 次检索 → 预算化上下文组装 → 生成 → 引用校验）两条链路复用同一份数据与同一份 Redis 会话记忆；前端用 hash 路由做通道分流，并把 Agentic 通道的证据、执行步骤、traceId 与校验结果透明展示；评测由 Python DeepEval（检索质量 + Agent 端到端驱动）与 Java LLM-as-a-Judge 回归（忠实性/正确性/区分能力）组成，语料与评测集同源于 TableRAG 论文。

架构上最值得肯定的两处，一是**流程控制权始终留在 Java**（上限写死为常量、失败走结构化降级、证据不足稳定拒答），二是**超时与降级策略被显式记录在代码与配置注释中**（60s < 65s < 90s 层级、OOM 熔断、纯向量兜底）。当前最需要偿还的技术债集中在**重启后 BM25 文档身份丢失**、**配置项与实际生效值不一致**、以及**评测口径仍带早期占位实现**三处，详见第 8 节与第 10 节的优先级建议。
