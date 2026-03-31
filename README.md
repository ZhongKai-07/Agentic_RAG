# Agentic RAG Knowledge Base

企业级 RAG（检索增强生成）知识库问答系统，支持智能代理检索、多轮对话和流式响应。

## 功能特性

- **异步文档处理流水线**：上传 → 解析（Docling）→ 语义分块 → LLM 元数据抽取 → 批量向量化 → OpenSearch 入库
- **混合检索**：BM25 + KNN 向量检索，支持多维度过滤
- **多知识空间**：每个 Space 独立索引，支持 BU/TEAM/USER 级别权限管控
- **WebSocket 实时通知**：STOMP over SockJS，文档处理状态实时推送
- **多轮对话**（Plan 4，开发中）：ReAct Agent、流式输出、引用溯源
- **SPI 可插拔**：本地（docling + OpenSearch）/ 云端（AWS Bedrock + AWS OpenSearch）零代码切换

## 技术栈

| 层次 | 技术 |
|------|------|
| 语言 / 框架 | Java 21 · Spring Boot 3.4.5 |
| AI | Spring AI 1.0.0 · AliCloud DashScope（qwen-plus · text-embedding-v3） |
| 向量数据库 | OpenSearch 2.17（KNN + BM25 混合） |
| 文档解析 | Docling-serve（REST API） |
| 关系数据库 | PostgreSQL 16 + Flyway |
| 缓存 | Redis 7 |
| 构建 | Maven 多模块 |

## 快速开始

### 前置依赖

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### 1. 启动基础设施

```bash
# Plan 1-2（仅需 DB + Cache）
cd docker && docker compose up -d postgresql redis

# Plan 3+（全套服务）
cd docker && docker compose up -d
```

| 服务 | 端口 |
|------|------|
| PostgreSQL | 5432 |
| Redis | 6379 |
| OpenSearch | 9200 |
| OpenSearch Dashboards | 5601 |
| Docling | 5001 |

### 2. 配置本地环境

```bash
cp rag-boot/src/main/resources/application-local.yml.template \
   rag-boot/src/main/resources/application-local.yml
# 填入 AliCloud DashScope API Key
```

### 3. 构建并启动

```bash
mvn clean install -DskipTests
mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=local
```

应用启动于 `http://localhost:8080`

## 模块结构

```
rag-boot                   # 启动入口、Flyway 迁移、全局配置
├── rag-adapter-inbound    # REST 控制器、WebSocket、DTO
│   └── rag-application    # 命令/查询处理、事件 Handler（无业务逻辑）
│       └── rag-domain     # 领域模型、Port 接口（零框架依赖）
├── rag-adapter-outbound   # JPA 实体/Repository、外部服务适配器
│   └── rag-infrastructure
└── rag-infrastructure     # ServiceRegistryConfig、Redis/OpenSearch 配置
```

> **核心约束**：`rag-domain` 禁止引入 Spring / JPA，仅允许 `reactor-core`（Flux）。

## 架构概览

### 领域划分

| 限界上下文 | 聚合根 | 包路径 |
|----------|--------|--------|
| Identity | User, KnowledgeSpace | `com.rag.domain.identity` |
| Document | Document（含版本） | `com.rag.domain.document` |
| Knowledge | KnowledgeChunk | `com.rag.domain.knowledge` |
| Conversation | ChatSession（含消息） | `com.rag.domain.conversation` |

### 文档处理流水线

```
上传文件
  → DocumentUploadedEvent
  → ParseEventHandler（@Async）
      → docling-serve 解析 + 语义分块
  → DocumentParsedEvent
  → IndexEventHandler（@Async）
      → LLM 元数据抽取（可选）
      → 批量 Embedding
      → OpenSearch 入库
  → ChunksIndexedEvent
  → WebSocket 推送（/topic/documents/{id}）
```

### SPI 适配器（@Profile 切换）

| Port 接口 | local | aws |
|----------|-------|-----|
| LlmPort | AliCloud DashScope（Spring AI） | 公司 LLM 网关 |
| EmbeddingPort | AliCloud text-embedding-v3 | 网关 |
| VectorStorePort | Local OpenSearch | AWS OpenSearch |
| DocParserPort | Docling-serve REST | AWS Bedrock |
| FileStoragePort | 本地文件系统 | S3 |

切换环境：`--spring.profiles.active=aws`（零代码变更）

## API 参考

- 基础路径：`/api/v1/`
- 用户标识：请求头 `X-User-Id: <uuid>`
- 文件上传：multipart，最大 100MB
- 分页：`?page=0&size=20&search=keyword`
- 错误格式：`{"error": "...", "message": "...", "timestamp": "..."}`

### 主要端点

```
POST   /api/v1/spaces                          # 创建知识空间
GET    /api/v1/spaces                          # 列出知识空间
POST   /api/v1/spaces/{spaceId}/documents/upload  # 上传文档（触发处理流水线）
GET    /api/v1/spaces/{spaceId}/documents      # 文档列表
GET    /api/v1/users                           # 用户列表
WS     /ws/notifications                       # WebSocket（STOMP/SockJS）
```

## 开发进度

- [x] Plan 1：项目骨架（模块、Docker、DB Schema、SPI 接口）
- [x] Plan 2：身份与文档管理（领域模型、JPA、REST API）
- [x] Plan 3：文档处理流水线（异步解析、分块、Embedding、入库）
- [ ] Plan 4：对话与 Agent 引擎（ReAct、流式输出、多轮、引用）
- [ ] Plan 5：React 前端

## 目录说明

```
docker/          # docker-compose.yml 及服务配置
docs/
└── superpowers/
    ├── specs/   # 系统设计规格书
    └── plans/   # 各阶段实施计划
rag-*/           # Maven 子模块
```
