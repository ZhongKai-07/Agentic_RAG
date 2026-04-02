# Hexagonal Architecture — 六边形体系架构

## 整体分层视图

```mermaid
graph TB
    subgraph External["外部世界"]
        Browser["🖥 React Frontend<br/>(Vite + TypeScript)"]
        PG["🐘 PostgreSQL 16"]
        Redis["📦 Redis 7"]
        OS["🔍 OpenSearch 2.17"]
        DashScope["☁ DashScope API<br/>(LLM / Embedding / Rerank)"]
        Docling["📄 Docling-serve<br/>(文档解析)"]
        FS["💾 Local Filesystem"]
    end

    subgraph Boot["rag-boot (启动层)"]
        App["RagApplication<br/>Spring Boot 3.4.5"]
        Flyway["Flyway 迁移"]
        Profiles["@Profile 切换<br/>local | aws"]
    end

    subgraph Inbound["rag-adapter-inbound (入站适配器)"]
        REST["REST Controllers<br/>ChatController<br/>DocumentController<br/>SpaceController"]
        SSE["SSE Streaming<br/>SseEmitter"]
        WS["WebSocket<br/>STOMP + SockJS"]
        Filter["CorrelationIdFilter<br/>链路追踪"]
        DTO["DTOs<br/>Request / Response"]
    end

    subgraph Application["rag-application (应用层)"]
        ChatApp["ChatApplicationService<br/>会话编排"]
        DocApp["DocumentApplicationService<br/>文档管理"]
        SpaceApp["SpaceApplicationService<br/>空间权限"]
        AgentImpl["Agent 实现<br/>LlmPlanner / HybridExecutor<br/>LlmEvaluator / LlmGenerator"]
        EventH["事件处理器<br/>ParseEventHandler<br/>IndexEventHandler"]
    end

    subgraph Domain["rag-domain (领域核心) ⭐"]
        direction TB
        Conv["Conversation 上下文<br/>ChatSession · Message<br/>StreamEvent · ChatService"]
        Agent["Agent 引擎<br/>AgentOrchestrator<br/>ReAct 循环"]
        Doc["Document 上下文<br/>Document · DocumentVersion<br/>DocumentStatus 状态机"]
        Identity["Identity 上下文<br/>User · KnowledgeSpace<br/>AccessRule · Authorization"]
        Knowledge["Knowledge 上下文<br/>KnowledgeChunk<br/>KnowledgeIndexService"]
        Ports["端口接口 (Ports)<br/>SessionRepository · LlmPort<br/>DocumentRepository · DocParserPort<br/>FileStoragePort · VectorStorePort<br/>EmbeddingPort · RerankPort<br/>SpaceRepository · UserRepository"]
    end

    subgraph Outbound["rag-adapter-outbound (出站适配器)"]
        JPA["JPA 持久化<br/>Entity → Mapper → Adapter"]
        VectorAdapter["LocalOpenSearchAdapter<br/>混合检索 + RRF"]
        LLMAdapter["AliCloudLlmAdapter<br/>Spring AI ChatClient"]
        EmbAdapter["AliCloudEmbeddingAdapter<br/>批量嵌入"]
        RerankAdapter["AliCloudRerankAdapter"]
        DocParser["DoclingJavaAdapter<br/>语义分块"]
        FileAdapter["LocalFileStorageAdapter"]
    end

    subgraph Infra["rag-infrastructure (基础设施层)"]
        SvcReg["ServiceRegistryConfig<br/>SPI 统一配置"]
        AsyncCfg["AsyncConfig<br/>线程池 + MDC"]
        OSConfig["OpenSearchConfig"]
        RedisCfg["RedisConfig"]
        AgentCfg["AgentConfig"]
    end

    %% 外部连接
    Browser -->|HTTP / SSE / WS| REST
    JPA -->|JDBC| PG
    JPA -->|Cache| Redis
    VectorAdapter -->|REST Client| OS
    LLMAdapter -->|Spring AI| DashScope
    EmbAdapter -->|Spring AI| DashScope
    RerankAdapter -->|WebClient| DashScope
    DocParser -->|WebClient| Docling
    FileAdapter -->|java.nio| FS

    %% 内部依赖流向
    REST --> ChatApp
    REST --> DocApp
    REST --> SpaceApp
    SSE --> ChatApp
    WS --> EventH

    ChatApp --> Conv
    ChatApp --> Agent
    DocApp --> Doc
    SpaceApp --> Identity
    AgentImpl --> Knowledge
    EventH --> Doc
    EventH --> Knowledge

    Agent --> Ports
    Conv --> Ports
    Doc --> Ports
    Identity --> Ports
    Knowledge --> Ports

    Ports -.->|实现| JPA
    Ports -.->|实现| VectorAdapter
    Ports -.->|实现| LLMAdapter
    Ports -.->|实现| EmbAdapter
    Ports -.->|实现| RerankAdapter
    Ports -.->|实现| DocParser
    Ports -.->|实现| FileAdapter

    Infra --> Outbound
    Boot --> Inbound
    Boot --> Outbound
    Boot --> Infra

    style Domain fill:#1a1a2e,stroke:#e94560,stroke-width:3px,color:#fff
    style Ports fill:#16213e,stroke:#0f3460,stroke-width:2px,color:#fff
    style Inbound fill:#0f3460,stroke:#533483,color:#fff
    style Outbound fill:#0f3460,stroke:#533483,color:#fff
    style Application fill:#16213e,stroke:#e94560,color:#fff
    style Infra fill:#222,stroke:#666,color:#fff
    style Boot fill:#222,stroke:#666,color:#fff
    style External fill:#333,stroke:#999,color:#fff
```

## 依赖规则（从内到外）

```
                    ┌─────────────────────────────┐
                    │   rag-domain (领域核心)        │  ← 最内层，零外部依赖
                    │   只依赖 reactor-core          │     只定义 Port 接口
                    └──────────────┬──────────────┘
                                   │ 依赖
                    ┌──────────────▼──────────────┐
                    │   rag-application (应用层)    │  ← 编排用例
                    │   依赖: rag-domain            │     实现 Agent 端口
                    └──────────────┬──────────────┘
                                   │ 依赖
              ┌────────────────────┼────────────────────┐
              │                    │                     │
    ┌─────────▼────────┐  ┌───────▼────────┐  ┌────────▼───────┐
    │ rag-adapter-      │  │ rag-adapter-    │  │ rag-            │
    │ inbound            │  │ outbound        │  │ infrastructure  │
    │                    │  │                 │  │                 │
    │ 依赖:              │  │ 依赖:           │  │ 依赖:           │
    │  rag-application   │  │  rag-domain     │  │  rag-domain     │
    │                    │  │  rag-infra      │  │                 │
    └────────┬───────────┘  └───────┬────────┘  └────────┬───────┘
             │                      │                     │
    ┌────────▼──────────────────────▼─────────────────────▼───────┐
    │                     rag-boot (启动层)                         │
    │   聚合所有模块，提供 main()、配置文件、Flyway 迁移              │
    └─────────────────────────────────────────────────────────────┘
```

## 关键约束

| 规则 | 说明 |
|------|------|
| **rag-domain 零框架依赖** | 不允许 import Spring、JPA、Jackson。唯一允许 reactor-core (Flux) |
| **依赖方向：外→内** | 外层依赖内层，内层不知道外层存在 |
| **Port 定义在 domain** | 所有外部服务接口定义在领域层 |
| **Adapter 实现 Port** | 出站适配器实现领域端口，通过 @Profile 切换 |
| **Application 不含业务逻辑** | 只做编排：加载→校验→调用 domain service→持久化 |
| **Entity ≠ Domain Model** | JPA Entity 在 adapter-outbound，通过 Mapper 转换，永不暴露到外层 |
