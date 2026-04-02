# SPI Adapter Mapping — 可插拔适配器

## Port → Adapter 映射

```mermaid
graph LR
    subgraph "rag-domain (端口定义)"
        LlmPort["LlmPort<br/>streamChat() · chat()"]
        EmbPort["EmbeddingPort<br/>embed() · embedBatch()"]
        RerankPort["RerankPort<br/>rerank()"]
        VecPort["VectorStorePort<br/>upsertChunks() · hybridSearch()<br/>deleteByDocumentId()"]
        DocParser["DocParserPort<br/>parse()"]
        FileStor["FileStoragePort<br/>store() · retrieve() · delete()"]
        SessionRepo["SessionRepository<br/>save() · findById()<br/>saveMessage()"]
        DocRepo["DocumentRepository<br/>save() · findById()<br/>findBySpaceId()"]
        SpaceRepo["SpaceRepository<br/>save() · findAccessible()"]
        UserRepo["UserRepository<br/>findById()"]
    end

    subgraph "@Profile(\"local\")"
        LlmLocal["AliCloudLlmAdapter<br/>Spring AI ChatClient<br/>→ DashScope qwen-plus"]
        EmbLocal["AliCloudEmbeddingAdapter<br/>Spring AI EmbeddingModel<br/>→ DashScope text-embedding-v3<br/>batch≤10, truncate 6000"]
        RerankLocal["AliCloudRerankAdapter<br/>WebClient<br/>→ DashScope gte-rerank"]
        VecLocal["LocalOpenSearchAdapter<br/>OpenSearch RestClient<br/>→ OpenSearch 2.17<br/>KNN + BM25 + RRF"]
        DocParserLocal["DoclingJavaAdapter<br/>WebClient<br/>→ Docling-serve<br/>/v1alpha/convert/file"]
        FileLocal["LocalFileStorageAdapter<br/>java.nio Files<br/>→ ./uploads/"]
        SessionLocal["SessionRepositoryAdapter<br/>Spring Data JPA<br/>→ PostgreSQL"]
        DocLocal["DocumentRepositoryAdapter<br/>Spring Data JPA<br/>→ PostgreSQL"]
        SpaceLocal["SpaceRepositoryAdapter<br/>Spring Data JPA<br/>→ PostgreSQL"]
        UserLocal["UserRepositoryAdapter<br/>Spring Data JPA<br/>→ PostgreSQL"]
    end

    subgraph "@Profile(\"aws\") (planned)"
        LlmAws["Gateway LLM Adapter"]
        EmbAws["Gateway Embedding Adapter"]
        VecAws["AWS OpenSearch Adapter"]
        DocParserAws["Bedrock Data Automation"]
        FileAws["S3 File Storage"]
    end

    LlmPort --> LlmLocal
    LlmPort -.-> LlmAws
    EmbPort --> EmbLocal
    EmbPort -.-> EmbAws
    RerankPort --> RerankLocal
    VecPort --> VecLocal
    VecPort -.-> VecAws
    DocParser --> DocParserLocal
    DocParser -.-> DocParserAws
    FileStor --> FileLocal
    FileStor -.-> FileAws
    SessionRepo --> SessionLocal
    DocRepo --> DocLocal
    SpaceRepo --> SpaceLocal
    UserRepo --> UserLocal

    style LlmPort fill:#e94560,stroke:#fff,color:#fff
    style EmbPort fill:#e94560,stroke:#fff,color:#fff
    style RerankPort fill:#e94560,stroke:#fff,color:#fff
    style VecPort fill:#e94560,stroke:#fff,color:#fff
    style DocParser fill:#e94560,stroke:#fff,color:#fff
    style FileStor fill:#e94560,stroke:#fff,color:#fff
    style SessionRepo fill:#e94560,stroke:#fff,color:#fff
    style DocRepo fill:#e94560,stroke:#fff,color:#fff
    style SpaceRepo fill:#e94560,stroke:#fff,color:#fff
    style UserRepo fill:#e94560,stroke:#fff,color:#fff
```

## 统一配置入口 — ServiceRegistryConfig

```yaml
# application-local.yml
rag:
  services:
    llm:
      api-key: ${DASHSCOPE_API_KEY}
      model: qwen-plus
      base-url: https://dashscope.aliyuncs.com/compatible-mode
    embedding:
      api-key: ${DASHSCOPE_API_KEY}
      model: text-embedding-v3
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      dimension: 1024
    rerank:
      api-key: ${DASHSCOPE_API_KEY}
      model: gte-rerank
      base-url: https://dashscope.aliyuncs.com/api/v1
    vector-store:
      url: http://localhost:9200
      username: admin
      password: admin
    doc-parser:
      base-url: http://localhost:5001
    file-storage:
      base-path: ./uploads
```

## 切换环境

```bash
# 本地开发
mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=local

# AWS 部署 (零代码修改)
mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=aws
```

所有适配器通过 `@Profile` 注解注册，Spring 根据激活的 profile 自动选择实现。
