# Maven Module Dependency — 模块依赖关系

## 模块依赖图

```mermaid
graph BT
    domain["rag-domain<br/>──────────────<br/>领域模型 · 端口接口<br/>领域服务 · 领域事件<br/>Agent 编排引擎<br/>──────────────<br/>依赖: reactor-core"]

    app["rag-application<br/>──────────────<br/>用例编排<br/>Agent 端口实现<br/>事件处理器<br/>──────────────<br/>依赖: rag-domain<br/>slf4j · jackson"]

    infra["rag-infrastructure<br/>──────────────<br/>ServiceRegistryConfig<br/>AsyncConfig · AgentConfig<br/>OpenSearch/Redis Config<br/>──────────────<br/>依赖: rag-domain<br/>spring-boot"]

    inbound["rag-adapter-inbound<br/>──────────────<br/>REST Controllers<br/>SSE · WebSocket<br/>DTOs · Filter<br/>──────────────<br/>依赖: rag-application<br/>spring-web · websocket"]

    outbound["rag-adapter-outbound<br/>──────────────<br/>JPA Entity/Repo/Adapter<br/>OpenSearch · DashScope<br/>Docling · FileStorage<br/>──────────────<br/>依赖: rag-domain<br/>rag-infrastructure<br/>spring-data-jpa<br/>opensearch-java<br/>spring-ai"]

    boot["rag-boot<br/>──────────────<br/>RagApplication.java<br/>application.yml<br/>Flyway migrations<br/>──────────────<br/>聚合所有模块"]

    frontend["rag-frontend<br/>──────────────<br/>React 18 · Vite<br/>TypeScript · Zustand<br/>Radix UI · TailwindCSS<br/>──────────────<br/>独立 npm 项目"]

    app --> domain
    infra --> domain
    inbound --> app
    outbound --> domain
    outbound --> infra
    boot --> inbound
    boot --> outbound
    boot --> infra
    frontend -.->|HTTP / SSE / WS| inbound

    style domain fill:#e94560,stroke:#fff,stroke-width:2px,color:#fff
    style app fill:#533483,stroke:#fff,color:#fff
    style infra fill:#0f3460,stroke:#fff,color:#fff
    style inbound fill:#16213e,stroke:#fff,color:#fff
    style outbound fill:#16213e,stroke:#fff,color:#fff
    style boot fill:#1a1a2e,stroke:#fff,color:#fff
    style frontend fill:#2d6a4f,stroke:#fff,color:#fff
```

## 依赖矩阵

| 模块 ↓ 依赖 → | domain | application | infrastructure | adapter-in | adapter-out | boot |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **rag-domain** | — | | | | | |
| **rag-application** | **✓** | — | | | | |
| **rag-infrastructure** | **✓** | | — | | | |
| **rag-adapter-inbound** | (传递) | **✓** | | — | | |
| **rag-adapter-outbound** | **✓** | | **✓** | | — | |
| **rag-boot** | (传递) | (传递) | **✓** | **✓** | **✓** | — |

> **✓** = 直接 Maven 依赖 &nbsp;&nbsp; **(传递)** = 通过其他模块间接获得

## 外部依赖分布

```
rag-domain
├── reactor-core          (Flux<StreamEvent> 流式响应)
└── (无其他框架依赖)

rag-application
├── rag-domain
├── slf4j-api             (日志)
└── jackson-databind      (JSON 解析)

rag-infrastructure
├── rag-domain
├── spring-boot-starter
├── spring-data-redis
└── opensearch-java

rag-adapter-inbound
├── rag-application
├── spring-boot-starter-web
├── spring-boot-starter-websocket
└── spring-boot-starter-validation

rag-adapter-outbound
├── rag-domain
├── rag-infrastructure
├── spring-boot-starter-data-jpa
├── spring-ai-openai-spring-boot-starter
├── opensearch-java
└── spring-boot-starter-webflux (WebClient)

rag-boot
├── (所有模块)
├── spring-boot-starter
├── flyway-core
└── postgresql (driver)
```
