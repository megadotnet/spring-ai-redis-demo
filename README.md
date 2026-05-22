# Spring AI Redis Demo 技术架构与使用指南

本项目是一个基于 Spring Boot 3.2.3 和 Spring AI 1.0.0 的检索增强生成（RAG）演示应用，结合了 Milvus 向量数据库和大型语言模型（LLM）来实现基于领域知识库的智能问答系统。项目采用前后端分离架构，前端使用 React 构建，后端利用 Spring 生态集成各项 AI 能力，支持混合检索（BM25 + 向量检索）、精排（Rerank）以及数据持久化。

---

## 🛠️ 技术栈清单

以下梳理了本项目各核心模块采用的具体技术选型及其版本和核心作用。

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
| **Spring Boot** | `3.2.3` | 应用脚手架，实现自动装配、快速构建生产级 RESTful API 服务。 |
| **Spring AI** | `1.0.0` | 统一抽象 LLM 和向量数据库交互，支持 Ollama、OpenAI 及 Milvus。 |
| **Apache Lucene** | `9.11.1` | 强大的文本搜索引擎库，用于实现本地化高性能的 BM25 关键词检索。 |
| **Apache POI** | `5.2.3` | 办公文档解析库（`poi` 及 `poi-ooxml`），辅助解析导入的结构化文档。 |

### 3. 🗄️ 数据库系统与基础设施 (Infrastructure)

| 技术/组件 | 版本号 | 核心作用说明 |
| --- | --- | --- |
| **Milvus** | 最新稳定版 | 高性能向量数据库，存储高维嵌入向量（Embeddings）并实现快速的 Top-K 语义相似度搜索。 |
| **Redis** | Redis Stack Server | 用作高性能 KV 缓存，并借助 Spring Data Redis 持久化 BM25 全文索引，支持应用重启后的索引恢复。 |
| **Ollama** | 最新稳定版 | 本地运行的 LLM 引擎，用于执行 `qwen2.5:0.5b` 等语言模型及 `bge-m3:latest` 等嵌入模型。 |

### 4. 🧰 工具链与构建部署 (Toolchain)

| 技术/组件 | 版本号 | 核心作用说明 |
| --- | --- | --- |
| **Maven** | `3.6+` | 后端项目依赖管理及构建工具。 |
| **Node.js & npm** | Node `v18.16.0`+, npm `9.5.1`+ | 前端运行环境及包管理工具。 |
| **Frontend Maven Plugin**| `1.15.0` | 在 Maven 构建生命周期中自动构建前端项目（如下载 Node 并执行 `npm run build`）。 |
| **Docker & Compose** | `20+` | 容器化技术，统一编排 Redis、Milvus、应用本身等组件的部署环境，解决依赖冲突问题。 |
| **JaCoCo** | `0.8.8` | 代码覆盖率工具，在测试阶段生成统计报告，保障代码质量。 |

---

## 💻 环境依赖要求

为了避免环境冲突，请确保您的开发和运行环境满足以下最低兼容版本：

* **Java**: `JDK 17` 及以上
* **Maven**: `3.6.3` 及以上
* **Node.js**: `v18.16.0` 及以上 (推荐 LTS 版本)
* **npm**: `9.5.1` 及以上
* **Docker**: 最新稳定版
* **Docker Compose**: `v2.0.0` 及以上

---

## 🚀 本地部署与启动步骤

适配 Windows、macOS、Linux 主流开发环境的可直接执行指令如下。

### 1. 启动基础设施依赖 (Milvus, Redis 等)

利用 Docker Compose 一键启动依赖组件：
```bash
# 进入项目根目录
cd spring-ai-redis-demo

# 启动基础设施容器
docker-compose up -d
```

### 2. 下载并运行本地大模型 (Ollama)

确保你已经安装了 [Ollama](https://ollama.com/)：
```bash
# 下载并运行嵌入模型 (1024 维向量)
ollama pull bge-m3:latest

# 下载并运行对话模型
ollama pull qwen2.5:0.5b
```

### 3. 配置环境变量 (可选，如果需要使用 Rerank 精排或外部云 Redis)

在终端或环境变量设置中导出所需的 Key（示例为 Unix/macOS，Windows 使用 `set`）：
```bash
# Windows
set SILICONFLOW_KEY=你的硅基流动API_KEY

# macOS / Linux
export SILICONFLOW_KEY=你的硅基流动API_KEY
```

### 4. 编译与启动后端服务

由于使用了 `frontend-maven-plugin`，Maven 会在构建时自动完成前端的下载、依赖安装与编译。

```bash
# Windows
mvnw.cmd clean install -DskipTests
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw clean install -DskipTests
./mvnw spring-boot:run
```

应用启动后，将自动在端口 `8080` 监听。访问 [http://localhost:8080](http://localhost:8080) 即可看到前端 React 界面。

*(附：单独启动前端的命令：)*
```bash
cd frontend
npm install
npm start
```

---

## 📂 项目结构说明

```text
spring-ai-redis-demo/
├── frontend/                       # React 前端工程目录
│   ├── public/                     # 静态资源 (HTML 模板、图标等)
│   ├── src/                        # 前端源代码
│   │   ├── Components/             # 界面组件 (如 ChatWindow)
│   │   ├── App.js / api.js         # 前端根组件与 API 请求封装
│   └── package.json                # 前端依赖配置
├── src/
│   ├── main/
│   │   ├── java/com/redis/demo/    # 后端 Java 核心业务逻辑
│   │   │   ├── RagApplication.java # Spring Boot 启动类
│   │   │   ├── RagController.java  # REST API 路由
│   │   │   ├── RagConfiguration.java# AI 及 向量数据库 Bean 配置
│   │   │   ├── RagDataLoader.java  # 应用启动时初始数据的预处理和注入
│   │   │   └── service/            # RAG、精排、BM25搜索等业务服务
│   │   └── resources/              # 后端配置 (application.properties 等)
│   └── test/                       # 单元测试与集成测试
├── Dockerfile                      # 应用容器构建文件
├── docker-compose.yml              # 依赖服务编排文件
└── pom.xml                         # Maven 构建与依赖管理配置文件
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
    - Java：遵循 Spring Boot 官方编码规范，并利用 Lombok `@Slf4j`, `@Data` 简化代码。
    - 前端：遵循 ESLint + Prettier，采用函数式组件与 Hooks 为主（在逐步迁移旧 Class 组件的情况下）。
4. **组件职责**：
    - 保持单一职责原则。Controller 层只处理 HTTP 请求包装；具体大模型拼接提示、检索流程交由 Service 层处理。

---

## ❓ 常见问题排查 (Troubleshooting)

1. **前端编译失败或 Node 找不到**
    - **问题表现**：运行 `mvn clean install` 时在 `frontend-maven-plugin` 步骤报错。
    - **解决方案**：检查系统是否正确安装了 Node.js（`v18+`），或者清理 Maven 缓存后重试。也可以手动进入 `frontend` 目录执行 `npm install` 与 `npm run build`。

2. **Milvus 连接被拒绝 / Connection Refused**
    - **问题表现**：后端启动时抛出 `Failed to connect to Milvus` 异常。
    - **解决方案**：检查 Docker Compose 是否已完全启动（`docker ps` 确保 milvus 状态为 `healthy` 或 `running`）；若部署在远程，请确保 `application.properties` 中的 `host` IP 能够连通。

3. **Ollama 模型拉取失败或超时**
    - **问题表现**：对话时后端日志报错提示模型调用超时或 404。
    - **解决方案**：确保你事先通过 `ollama pull <model_name>` 将 `qwen2.5` 等模型成功拉取到本地，并检查 `http://127.0.0.1:11434` 服务是否存活。

4. **Redis OOM (内存溢出) 问题**
    - **问题表现**：加载海量数据建立 BM25 索引持久化时，Redis 崩溃。
    - **解决方案**：后端应用已具备自动降级至本地 Lucene 内存索引机制，请监控 Redis 内存分配；如有需要，可以在 `docker-compose.yml` 中配置调大 Redis 容器的最大可用内存。
