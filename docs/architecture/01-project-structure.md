# Project Structure — Maven Multi-Module

```
Agentic_RAG/
├── pom.xml                          # Parent POM (Spring Boot 3.4.5, Spring AI 1.0.0)
│
├── rag-boot/                        # 启动层 — Entry Point
│   ├── src/main/java/com/rag/
│   │   └── RagApplication.java      # @SpringBootApplication + @EnableAsync
│   └── src/main/resources/
│       ├── application.yml           # 通用配置
│       ├── application-local.yml     # 本地开发配置 (gitignored, 含 API keys)
│       └── db/migration/             # Flyway SQL 迁移脚本
│
├── rag-domain/                      # 领域层 — Pure Business Logic (零框架依赖)
│   └── src/main/java/com/rag/domain/
│       ├── conversation/            # 对话上下文
│       │   ├── ChatSession.java     # 聚合根：会话 + 历史截断
│       │   ├── Message.java         # 消息（含引用 + Agent 追踪）
│       │   ├── Citation.java        # 引用元数据
│       │   ├── AgentTrace.java      # 多轮检索追踪
│       │   ├── StreamEvent.java     # sealed interface — SSE 事件类型
│       │   ├── ChatService.java     # 会话生命周期
│       │   ├── SessionRepository.java  # Port: 会话持久化
│       │   ├── LlmPort.java         # Port: LLM 调用
│       │   └── agent/               # Agent ReAct 引擎
│       │       ├── AgentOrchestrator.java   # 核心循环：Plan→Act→Eval→Gen
│       │       ├── RetrievalPlanner.java    # Port: 查询规划
│       │       ├── RetrievalExecutor.java   # Port: 检索执行
│       │       ├── RetrievalEvaluator.java  # Port: 结果评估
│       │       ├── AnswerGenerator.java     # Port: 答案生成
│       │       └── model/                   # Agent 值对象
│       │           ├── AgentRequest.java
│       │           ├── RetrievalPlan.java
│       │           ├── RetrievalResult.java
│       │           ├── EvaluationResult.java
│       │           └── ...
│       │
│       ├── document/                # 文档上下文
│       │   ├── Document.java        # 聚合根：文档 + 状态机
│       │   ├── DocumentVersion.java # 版本管理
│       │   ├── DocumentStatus.java  # 状态枚举 + 转换规则
│       │   ├── DocumentLifecycleService.java
│       │   ├── DocumentRepository.java      # Port: 文档持久化
│       │   ├── DocParserPort.java           # Port: 文档解析
│       │   ├── FileStoragePort.java         # Port: 文件存储
│       │   └── *Event.java                  # 领域事件
│       │
│       ├── identity/                # 身份与权限上下文
│       │   ├── User.java
│       │   ├── KnowledgeSpace.java  # 聚合根：知识空间
│       │   ├── AccessRule.java      # 权限规则 (BU/TEAM/USER)
│       │   ├── RetrievalConfig.java # 检索配置 (JSONB)
│       │   ├── SpaceAuthorizationService.java
│       │   ├── SpaceRepository.java         # Port
│       │   └── UserRepository.java          # Port
│       │
│       ├── knowledge/               # 知识上下文
│       │   ├── KnowledgeChunk.java  # 知识分块
│       │   ├── KnowledgeIndexService.java
│       │   ├── VectorStorePort.java         # Port: 向量存储
│       │   ├── EmbeddingPort.java           # Port: 文本嵌入
│       │   └── RerankPort.java              # Port: 重排序
│       │
│       └── shared/                  # 共享内核
│           ├── PageResult.java
│           ├── SecurityLevel.java
│           └── DomainEvent.java     # sealed marker
│
├── rag-application/                 # 应用层 — 用例编排 (无业务逻辑)
│   └── src/main/java/com/rag/application/
│       ├── chat/
│       │   └── ChatApplicationService.java      # 会话 CRUD + 聊天编排
│       ├── document/
│       │   └── DocumentApplicationService.java  # 文档上传/删除/重试
│       ├── identity/
│       │   └── SpaceApplicationService.java     # 空间管理 + 权限校验
│       ├── agent/                               # Agent 端口实现
│       │   ├── LlmRetrievalPlanner.java         # LLM 查询规划
│       │   ├── HybridRetrievalExecutor.java     # 混合检索 + RRF 融合
│       │   ├── LlmRetrievalEvaluator.java       # LLM 结果评估
│       │   └── LlmAnswerGenerator.java          # 流式答案生成 + 引用提取
│       └── event/                               # 异步事件处理
│           ├── ParseEventHandler.java           # 文档解析流水线
│           └── IndexEventHandler.java           # 嵌入 + 索引流水线
│
├── rag-adapter-inbound/             # 入站适配器 — REST / WebSocket / SSE
│   └── src/main/java/com/rag/adapter/inbound/
│       ├── rest/
│       │   ├── ChatController.java              # /api/v1/sessions/*
│       │   ├── DocumentController.java          # /api/v1/spaces/{id}/documents/*
│       │   ├── SpaceController.java             # /api/v1/spaces/*
│       │   ├── UserController.java              # /api/v1/users/*
│       │   ├── HealthController.java
│       │   └── GlobalExceptionHandler.java      # 统一异常处理
│       ├── dto/
│       │   ├── request/   (ChatRequest, CreateSpaceRequest, ...)
│       │   └── response/  (SessionResponse, DocumentResponse, ...)
│       ├── filter/
│       │   └── CorrelationIdFilter.java         # X-Correlation-Id 链路追踪
│       └── websocket/
│           ├── WebSocketConfig.java             # STOMP + SockJS
│           └── DocumentStatusNotifier.java      # 文档状态推送
│
├── rag-adapter-outbound/            # 出站适配器 — 外部服务集成
│   └── src/main/java/com/rag/adapter/outbound/
│       ├── persistence/             # JPA 持久化
│       │   ├── entity/     (DocumentEntity, ChatSessionEntity, ...)
│       │   ├── repository/ (DocumentJpaRepository, ...)
│       │   ├── adapter/    (DocumentRepositoryAdapter, ...)
│       │   └── mapper/     (DocumentMapper, ChatSessionMapper, ...)
│       ├── vectorstore/
│       │   └── LocalOpenSearchAdapter.java      # OpenSearch 混合检索 + RRF
│       ├── llm/
│       │   └── AliCloudLlmAdapter.java          # DashScope LLM (Spring AI)
│       ├── embedding/
│       │   └── AliCloudEmbeddingAdapter.java    # DashScope Embedding
│       ├── rerank/
│       │   └── AliCloudRerankAdapter.java       # DashScope Rerank
│       ├── docparser/
│       │   └── DoclingJavaAdapter.java          # Docling REST 文档解析
│       └── storage/
│           └── LocalFileStorageAdapter.java     # 本地文件系统
│
├── rag-infrastructure/              # 基础设施层 — 配置 & 装配
│   └── src/main/java/com/rag/infrastructure/config/
│       ├── ServiceRegistryConfig.java   # SPI 统一配置入口
│       ├── AsyncConfig.java             # 线程池 + MDC 传播
│       ├── OpenSearchConfig.java        # OpenSearch 客户端
│       ├── RedisConfig.java             # Redis 缓存
│       └── AgentConfig.java             # Agent 组件装配
│
├── rag-frontend/                    # 前端 — React + TypeScript
│   ├── src/
│   │   ├── api/             # Axios HTTP 客户端 + API 模块
│   │   ├── components/
│   │   │   ├── chat/        # 聊天界面 (消息、流式、引用、Agent 状态)
│   │   │   ├── documents/   # 文档管理 (上传、列表、状态)
│   │   │   ├── spaces/      # 知识空间管理
│   │   │   └── layout/      # 布局框架
│   │   ├── hooks/           # useSSE, useDocumentNotification
│   │   ├── stores/          # Zustand 状态管理
│   │   └── App.tsx          # 路由 + 入口
│   ├── vite.config.ts       # Dev proxy → :8080
│   └── package.json
│
└── docker/
    └── docker-compose.yml   # PostgreSQL, Redis, OpenSearch, Docling
```
