# Spring AI Redis Demo Technical Architecture and Usage Guide

English | [简体中文](ReadMe_Zhcn.md)

This project is a Retrieval-Augmented Generation (RAG) demonstration application based on Spring Boot 3.2.3 and Spring AI 1.0.0. It integrates the Milvus vector database and Large Language Models (LLMs) to implement an intelligent Q&A system based on a domain knowledge base. The project adopts a frontend-backend separation architecture: the frontend is built with React, and the backend leverages the Spring ecosystem to integrate various AI capabilities, supporting hybrid search (BM25 + vector search), reranking, and data persistence.

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
| **Spring Boot** | `3.2.3` | Application scaffolding, enabling auto-configuration and rapid construction of production-grade RESTful API services. |
| **Spring AI** | `1.0.0` | Unified abstraction for interacting with LLMs and vector databases, supporting Ollama, OpenAI, and Milvus. |
| **Apache Lucene** | `9.11.1` | Powerful text search engine library, used to implement localized, high-performance BM25 keyword search. |
| **Apache POI** | `5.2.3` | Office document parsing library (`poi` and `poi-ooxml`), assisting in parsing imported structured documents. |

### 3. 🗄️ Database Systems and Infrastructure

| Technology/Component | Version | Core Role Description |
| --- | --- | --- |
| **Milvus** | Latest Stable | High-performance vector database, storing high-dimensional embeddings and implementing fast Top-K semantic similarity search. |
| **Redis** | Redis Stack Server | Used as a high-performance KV cache, and through Spring Data Redis, it persists the BM25 full-text index, supporting index recovery after application restarts. |
| **Ollama** | Latest Stable | Locally run LLM engine, used to execute language models like `qwen2.5:0.5b` and embedding models like `bge-m3:latest`. |

### 4. 🧰 Toolchain and Build/Deployment

| Technology/Component | Version | Core Role Description |
| --- | --- | --- |
| **Maven** | `3.6+` | Backend project dependency management and build tool. |
| **Node.js & npm** | Node `v18.16.0`+, npm `9.5.1`+ | Frontend runtime environment and package management tool. |
| **Frontend Maven Plugin**| `1.15.0` | Automatically builds the frontend project during the Maven build lifecycle (e.g., downloading Node and executing `npm run build`). |
| **Docker & Compose** | `20+` | Containerization technology, orchestrating the deployment environment of Redis, Milvus, and the application itself to resolve dependency conflicts. |
| **JaCoCo** | `0.8.8` | Code coverage tool, generating statistical reports during the testing phase to ensure code quality. |

---

## 💻 Environmental Dependency Requirements

To avoid environmental conflicts, please ensure your development and runtime environments meet the following minimum compatible versions:

* **Java**: `JDK 17` or higher
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
# Enter the project root directory
cd spring-ai-redis-demo

# Start the infrastructure containers
docker-compose up -d
```

### 2. Download and Run Local Large Models (Ollama)

Ensure you have installed [Ollama](https://ollama.com/):
```bash
# Download and run the embedding model (1024-dimensional vectors)
ollama pull bge-m3:latest

# Download and run the conversation model
ollama pull qwen2.5:0.5b
```

### 3. Configure Environment Variables (Optional, if using Rerank or external Cloud Redis)

Export the required Keys in your terminal or environment variable settings (examples are for Unix/macOS; use `set` for Windows):
```bash
# Windows
set SILICONFLOW_KEY=your_siliconflow_api_key

# macOS / Linux
export SILICONFLOW_KEY=your_siliconflow_api_key
```

### 4. Compile and Start Backend Service

Because the `frontend-maven-plugin` is used, Maven will automatically handle frontend downloading, dependency installation, and compilation during the build process.

```bash
# Windows
mvnw.cmd clean install -DskipTests
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw clean install -DskipTests
./mvnw spring-boot:run
```

Once the application starts, it will automatically listen on port `8080`. Visit [http://localhost:8080](http://localhost:8080) to see the frontend React interface.

*(Appendix: Commands to start the frontend separately:)*
```bash
cd frontend
npm install
npm start
```

---

## 📂 Project Structure

```text
spring-ai-redis-demo/
├── frontend/                       # React frontend project directory
│   ├── public/                     # Static resources (HTML templates, icons, etc.)
│   ├── src/                        # Frontend source code
│   │   ├── Components/             # UI components (e.g., ChatWindow)
│   │   ├── App.js / api.js         # Frontend root component and API request encapsulation
│   └── package.json                # Frontend dependency configuration
├── src/
│   ├── main/
│   │   ├── java/com/redis/demo/    # Backend Java core business logic
│   │   │   ├── RagApplication.java # Spring Boot startup class
│   │   │   ├── RagController.java  # REST API routing
│   │   │   ├── RagConfiguration.java# AI and vector database Bean configuration
│   │   │   ├── RagDataLoader.java  # Pre-processing and injection of initial data on startup
│   │   │   └── service/            # RAG, rerank, BM25 search and other business services
│   │   └── resources/              # Backend configuration (application.properties, etc.)
│   └── test/                       # Unit and integration tests
├── Dockerfile                      # Application container build file
├── docker-compose.yml              # Dependency service orchestration file
└── pom.xml                         # Maven build and dependency management configuration file
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

1. **Frontend compilation failed or Node not found**
    - **Symptom**: Error occurs at the `frontend-maven-plugin` step when running `mvn clean install`.
    - **Solution**: Check if Node.js (`v18+`) is correctly installed on your system, or try clearing the Maven cache and trying again. Alternatively, manually enter the `frontend` directory to execute `npm install` and `npm run build`.

2. **Milvus Connection Refused**
    - **Symptom**: Backend throws `Failed to connect to Milvus` exception upon startup.
    - **Solution**: Check if Docker Compose is fully started (`docker ps` to ensure milvus status is `healthy` or `running`); if deployed remotely, ensure the `host` IP in `application.properties` is accessible.

3. **Ollama model pull failed or timed out**
    - **Symptom**: Backend logs indicate model call timeout or 404 during conversation.
    - **Solution**: Ensure you have successfully pulled the model (e.g., `qwen2.5`) locally beforehand via `ollama pull <model_name>`, and check if the `http://127.0.0.1:11434` service is alive.

4. **Redis OOM (Out Of Memory) Issues**
    - **Symptom**: Redis crashes when loading massive data to establish BM25 index persistence.
    - **Solution**: The backend application has a mechanism to automatically downgrade to a local Lucene in-memory index; please monitor Redis memory allocation. If necessary, you can configure to increase the maximum available memory for the Redis container in `docker-compose.yml`.
