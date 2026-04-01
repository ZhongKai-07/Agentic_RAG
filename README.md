# Agentic RAG Knowledge Base

企业级 RAG（检索增强生成）知识库问答系统，支持 Agent 式智能检索、多轮对话和流式响应。

## 功能特性

- **异步文档处理流水线**：上传 → Docling 解析 → 语义分块 → LLM 元数据抽取 → 批量 Embedding → OpenSearch 入库
- **RRF 混合检索**：BM25 全文检索 + KNN 向量检索，倒数秩融合（Reciprocal Rank Fusion）归一化
- **ReAct Agent 引擎**：规划（Planner）→ 检索（Executor）→ 评估（Evaluator）→ 生成（Generator），多轮迭代直到结果充分
- **SSE 流式问答**：实时流式输出 Agent 思考过程、检索状态、回答内容和引用溯源
- **多知识空间**：每个 Space 独立 OpenSearch 索引，BU/TEAM/USER 三级权限管控
- **WebSocket 实时通知**：STOMP over SockJS，文档处理状态实时推送
- **React 前端**：完整的知识空间管理、文档上传、对话问答界面
- **SPI 可插拔**：本地 / 云端零代码切换

## 技术栈

| 层次    | 技术                                                                          |
| ----- | --------------------------------------------------------------------------- |
| 后端    | Java 21 · Spring Boot 3.4.5 · Spring AI 1.0.0                               |
| AI 模型 | AliCloud DashScope（qwen-plus · text-embedding-v3 · gte-rerank）              |
| 向量数据库 | OpenSearch 2.17（KNN + BM25 混合，RRF 融合）                                       |
| 文档解析  | Docling-serve（REST API，支持 PDF/Word/Markdown）                                |
| 关系数据库 | PostgreSQL 16 + Flyway                                                      |
| 缓存    | Redis 7                                                                     |
| 前端    | React 18 · Vite · TypeScript · Zustand · Radix UI (shadcn/ui) · TailwindCSS |
| 构建    | Maven 多模块                                                                   |

***

## 快速启动指南

### 前置依赖

- Java 21+
- Maven 3.9+
- Node.js 18+（前端）
- Docker & Docker Compose

### Step 1：启动基础设施

```bash
cd docker && docker compose up -d
```

等待所有服务健康启动（首次拉取镜像较慢）：

| 服务                    | 端口   | 说明                          |
| --------------------- | ---- | --------------------------- |
| PostgreSQL            | 5432 | 关系数据库（用户、文档、会话）             |
| Redis                 | 6379 | 缓存                          |
| OpenSearch            | 9200 | 向量存储 + 全文检索                 |
| OpenSearch Dashboards | 5601 | 索引管理界面                      |
| Docling               | 5001 | 文档解析服务（PDF/Word → Markdown） |

> **提示**：如果不需要文档解析和向量检索，可以只启动核心服务：`docker compose up -d postgresql redis`

### Step 2：配置 API Key

编辑 `rag-boot/src/main/resources/application-local.yml`，填入 AliCloud DashScope API Key：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db?stringtype=unspecified
    username: rag_user
    password: rag_password
    driver-class-name: org.postgresql.Driver
  data:
    redis:
      host: localhost
      port: 6379
  ai:
    openai:
      api-key: <YOUR_DASHSCOPE_API_KEY>
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      chat:
        options:
          model: qwen-plus
          temperature: 0.7
      embedding:
        options:
          model: text-embedding-v3

rag:
  services:
    llm:
      api-key: <YOUR_DASHSCOPE_API_KEY>
      model: qwen-plus
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    embedding:
      api-key: <YOUR_DASHSCOPE_API_KEY>
      model: text-embedding-v3
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      dimension: 1024
    rerank:
      api-key: <YOUR_DASHSCOPE_API_KEY>
      model: gte-rerank
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    vector-store:
      url: http://localhost:9200
    doc-parser:
      url: http://localhost:5001
    file-storage:
      base-path: ./uploads
```

> 该文件已 gitignored，不会提交到仓库。

### Step 3：构建并启动后端

```bash
mvn clean install -DskipTests
mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=local
```

后端启动于 `http://localhost:8080`

### Step 4：初始化测试数据

<br />

首次启动后数据库为空，需要插入初始用户：

```sql
-- 连接数据库：psql -h localhost -U rag_user -d rag_db
INSERT INTO t_user (user_id, username, display_name, email, bu, team, role)
VALUES
  ('a0000000-0000-0000-0000-000000000001', 'admin', '管理员', 'admin@example.com', 'TECH', 'PLATFORM', 'ADMIN'),
  ('a0000000-0000-0000-0000-000000000002', 'zhangsan', '张三', 'zhangsan@example.com', 'TECH', 'AI', 'MEMBER'),
  ('a0000000-0000-0000-0000-000000000003', 'lisi', '李四', 'lisi@example.com', 'BUSINESS', 'SALES', 'MEMBER');
```

### Step 5：启动前端（可选）

```bash
cd rag-frontend
npm install
npm run dev
```

前端启动于 `http://localhost:3000`，自动代理 `/api` 和 `/ws` 到后端 `:8080`。

### 验证启动成功

```bash
# 检查后端健康
curl http://localhost:8080/api/v1/users/me \
  -H "X-User-Id: a0000000-0000-0000-0000-000000000001"

# 应返回用户信息 JSON
```

***

## 用户手册

### 核心概念

| 概念                | 说明                                |
| ----------------- | --------------------------------- |
| **知识空间 (Space)**  | 独立的知识库，拥有独立的 OpenSearch 索引和权限规则   |
| **文档 (Document)** | 上传到空间的文件（PDF/Word/Markdown），支持多版本 |
| **会话 (Session)**  | 与某个空间绑定的多轮对话上下文                   |
| **引用 (Citation)** | AI 回答中引用的原文片段，可溯源到具体文档和页码         |

### 基本工作流

```
创建用户 → 创建知识空间 → 上传文档 → 等待处理完成 → 创建会话 → 提问
```

### API 使用指南

所有请求需携带 `X-User-Id` 请求头标识用户身份。

#### 1. 创建知识空间

```bash
curl -X POST http://localhost:8080/api/v1/spaces \
  -H "Content-Type: application/json" \
  -H "X-User-Id: a0000000-0000-0000-0000-000000000001" \
  -d '{
    "name": "AI 技术文档",
    "description": "团队内部 AI 技术知识库",
    "ownerTeam": "AI",
    "language": "zh",
    "indexName": "kb-ai-docs"
  }'
```

创建者自动获得 MANAGEMENT 级别权限。

#### 2. 上传文档

```bash
curl -X POST http://localhost:8080/api/v1/spaces/{spaceId}/documents/upload \
  -H "X-User-Id: a0000000-0000-0000-0000-000000000001" \
  -F "file=@/path/to/document.pdf"
```

支持的文件类型：PDF、Word（.docx）、Markdown（.md）、文本（.txt）

上传后文档进入异步处理流水线：

```
UPLOADED → PARSING → PARSED → INDEXING → INDEXED
```

可通过 WebSocket 订阅 `/topic/documents/{documentId}` 获取实时状态更新。

#### 3. 查看文档状态

```bash
curl http://localhost:8080/api/v1/spaces/{spaceId}/documents \
  -H "X-User-Id: a0000000-0000-0000-0000-000000000001"
```

文档状态说明：

- `UPLOADED` — 已上传，等待解析
- `PARSING` — 正在解析中
- `PARSED` — 解析完成，等待向量化
- `INDEXING` — 正在生成 Embedding 并入库
- `INDEXED` — 完成，可以检索
- `FAILED` — 处理失败，可通过 `/retry` 重试

#### 4. 创建对话会话

```bash
curl -X POST http://localhost:8080/api/v1/spaces/{spaceId}/sessions \
  -H "Content-Type: application/json" \
  -H "X-User-Id: a0000000-0000-0000-0000-000000000001" \
  -d '{"title": "关于 RAG 架构的讨论"}'
```

#### 5. 提问（SSE 流式）

```bash
curl -N -X POST http://localhost:8080/api/v1/sessions/{sessionId}/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: a0000000-0000-0000-0000-000000000001" \
  -d '{"message": "什么是 RAG 架构？它有哪些优势？"}'
```

返回 SSE 事件流：

| 事件类型               | 说明            | 示例数据                                                          |
| ------------------ | ------------- | ------------------------------------------------------------- |
| `agent_thinking`   | Agent 正在分析查询  | `{"round":1,"content":"Analyzing query..."}`                  |
| `agent_searching`  | Agent 正在检索    | `{"round":1,"queries":["RAG architecture","RAG advantages"]}` |
| `agent_evaluating` | Agent 评估结果充分性 | `{"round":1,"sufficient":true}`                               |
| `content_delta`    | 流式回答内容片段      | `{"delta":"RAG（检索增强生成）是"}`                                    |
| `citation`         | 引用来源          | `{"citationIndex":1,"documentTitle":"...","snippet":"..."}`   |
| `done`             | 回答完成          | `{"messageId":"...","totalCitations":2}`                      |
| `error`            | 错误            | `{"code":"AGENT_ERROR","message":"..."}`                      |

#### 6. 权限管理

更新空间访问规则：

```bash
curl -X PUT http://localhost:8080/api/v1/spaces/{spaceId}/access-rules \
  -H "Content-Type: application/json" \
  -H "X-User-Id: a0000000-0000-0000-0000-000000000001" \
  -d '{
    "rules": [
      {"targetType": "TEAM", "targetValue": "AI", "docSecurityClearance": "MANAGEMENT"},
      {"targetType": "BU", "targetValue": "TECH", "docSecurityClearance": "ALL"}
    ]
  }'
```

权限层级：

- `BU` — 整个事业部可访问
- `TEAM` — 特定团队可访问
- `USER` — 指定个人可访问

安全级别：

- `ALL` — 可查看所有文档
- `MANAGEMENT` — 可查看管理层文档

### 请求追踪

所有响应包含 `X-Correlation-Id` 请求头。可在请求时传入自定义 ID：

```bash
curl -H "X-Correlation-Id: my-trace-123" http://localhost:8080/api/v1/spaces \
  -H "X-User-Id: ..."
```

错误响应自动包含 `requestId` 字段，方便排查：

```json
{
  "error": "NOT_FOUND",
  "message": "Space not found: ...",
  "requestId": "a1b2c3d4",
  "timestamp": "2026-04-01T12:00:00Z"
}
```

***

## 模块结构

```
rag-boot                   # 启动入口、Flyway 迁移、全局配置
├── rag-adapter-inbound    # REST 控制器、SSE、WebSocket、DTO、CorrelationIdFilter
│   └── rag-application    # 命令/查询处理、事件 Handler、Agent 实现（无业务逻辑）
│       └── rag-domain     # 领域模型、Port 接口、AgentOrchestrator（零框架依赖）
├── rag-adapter-outbound   # JPA 实体/Repository、外部服务适配器（LLM/Embedding/Rerank/OpenSearch）
│   └── rag-infrastructure
└── rag-infrastructure     # ServiceRegistryConfig、Redis/OpenSearch/Async 配置
rag-frontend               # React 前端（Vite + TypeScript + Zustand + shadcn/ui）
```

> **核心约束**：`rag-domain` 禁止引入 Spring / JPA，仅允许 `reactor-core`（Flux）和 `java.base`。

## 开发命令

```bash
# 构建
mvn clean package                    # 全量构建
mvn clean install -DskipTests        # 构建（跳过测试）
mvn compile -pl rag-domain -q        # 构建单个模块

# 运行
cd docker && docker compose up -d    # 启动全部基础设施
mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=local  # 后端 :8080

# 前端
cd rag-frontend && npm install && npm run dev    # 开发服务器 :3000
cd rag-frontend && npm run build                 # 生产构建
```

## 开发进度

- [x] Plan 1：项目骨架（模块、Docker、DB Schema、SPI 接口）
- [x] Plan 2：身份与文档管理（领域模型、JPA、REST API）
- [x] Plan 3：文档处理流水线（异步解析、分块、Embedding、入库）
- [x] Plan 4：对话与 Agent 引擎（ReAct、SSE 流式、多轮对话、引用溯源）
- [x] Plan 5：React 前端（知识空间管理、文档上传、对话问答界面）
- [x] Bug 修复 & 鲁棒性增强（2026-04-01 ~ 04-02）

## 当前状态（2026-04-02）

**系统可运行**，核心链路已打通：上传文档 → Docling 解析 → Embedding → OpenSearch 入库 → Chat 问答（ReAct Agent + SSE 流式）。

**已验证的模型配置：**
- LLM：kimi-k2.5（DashScope OpenAI 兼容模式）
- Embedding：text-embedding-v4（DashScope OpenAI 兼容模式）
- Rerank：gte-rerank-v2（DashScope 原生 API）
- 文档解析：docling-serve 0.5.1（`/v1alpha/convert/file`）

**已知限制：**
- 无用户注册 API，需手动 SQL 插入用户
- 无真实认证，仅 mock auth（X-User-Id header）
- 前端部分功能（批量标签、文档详情对话框）未完全联调
- 无自动化测试

详见 `dev_log/` 目录的开发日志。

