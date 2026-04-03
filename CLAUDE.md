# Agentic RAG Knowledge Base

Enterprise RAG Q&A chatbot with agentic retrieval, multi-turn conversation, and streaming responses.

**Stack:** Java 21 · Spring Boot 3.4.5 · Spring AI 1.0.0 · PostgreSQL 16 · OpenSearch 2.17 · Maven multi-module

**Frontend Stack:** React 18 · Vite · TypeScript · Zustand · Radix UI (shadcn/ui) · TailwindCSS · React Router v6 · STOMP/SockJS

## Commands

```bash
# Build
mvn clean package                    # Full build
mvn clean install -DskipTests        # Build without tests
mvn compile -pl rag-domain -q        # Build single module

# Run
cd docker && docker compose up -d postgresql redis   # Start DB + cache
mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=local  # Start app on :8080

# Test
mvn test                             # All tests
mvn test -pl rag-domain              # Single module tests

# Frontend
cd rag-frontend && npm install && npm run dev  # Start dev server on :3000 (proxies /api to :8080)
cd rag-frontend && npm run build               # Production build to dist/

# Docker (full stack)
cd docker && docker compose up -d    # All services (PG, Redis, OpenSearch, docling)
```

## Architecture

**DDD Hexagonal + CQRS + Event-Driven.** Six Maven modules:

```
rag-boot                          # Entry point, configs, Flyway migrations
├── rag-adapter-inbound           # REST controllers, SSE, WebSocket, DTOs
│   └── rag-application           # Command/query handlers, orchestration (no biz logic)
│       └── rag-domain            # Models, domain services, port interfaces (ZERO framework deps)
├── rag-adapter-outbound          # JPA entities/repos, SPI implementations, file storage
│   ├── rag-domain
│   └── rag-infrastructure
└── rag-infrastructure            # ServiceRegistryConfig, Redis/OpenSearch configs, SPI wiring
    └── rag-domain
```

**Critical constraint:** `rag-domain` must NOT import Spring, JPA, or any framework. Only `reactor-core` (for `Flux`).

## Bounded Contexts

| Context | Aggregate Root | Package |
|---------|---------------|---------|
| Identity | User, KnowledgeSpace | `com.rag.domain.identity` |
| Document | Document (with versions) | `com.rag.domain.document` |
| Knowledge | KnowledgeChunk | `com.rag.domain.knowledge` |
| Conversation | ChatSession (with messages) | `com.rag.domain.conversation` |

Inter-context communication: `DocumentUploadedEvent` → async parsing → `DocumentParsedEvent` → embedding + indexing → `ChunksIndexedEvent`.

## SPI Pluggable Mechanism

All external services are behind port interfaces in `rag-domain`. Implementations switch via `@Profile`:

| Port | local | aws |
|------|-------|-----|
| LlmPort | AliCloud DashScope (via Spring AI) | Company LLM Gateway |
| EmbeddingPort | AliCloud text-embedding-v3 (via Spring AI) | Gateway |
| RerankPort | AliCloud gte-rerank | Gateway |
| VectorStorePort | Local OpenSearch | AWS OpenSearch |
| DocParserPort | Docling-serve REST API | AWS Bedrock Data Automation |
| FileStoragePort | Local filesystem | S3 |

Switch environment: `--spring.profiles.active=aws` (zero code change).

**Unified config entry:** `ServiceRegistryConfig` in `rag-infrastructure` — all service connection details in one place, bound from `rag.services.*` in YAML.

## Key Patterns

- **Document State Machine:** `UPLOADED → PARSING → PARSED → INDEXING → INDEXED` (or `FAILED`). Enforced by `DocumentStatus.canTransitionTo()`.
- **Permission Model:** Knowledge Space per-index isolation + AccessRule (BU/TEAM/USER target types) + SecurityLevel (ALL/MANAGEMENT) query-time filter.
- **Entity ↔ Domain Mapping:** JPA entities in `adapter-outbound/persistence/entity/`, domain models in `domain/*/model/`. Mappers in `adapter-outbound/persistence/mapper/`. Never expose entities outside the adapter.
- **Repository Adapter Pattern:** Domain ports (`UserRepository`) → adapter implements (`UserRepositoryAdapter` @Component) → delegates to Spring Data JPA interface (`UserJpaRepository`).
- **Async Event Pipeline:** `@Async("documentProcessingExecutor")` + `@EventListener` in `rag-application/event/`. ParseEventHandler → IndexEventHandler. Thread pool configured in `AsyncConfig` (core=2, max=5, queue=50).
- **WebSocket Notifications:** STOMP over SockJS at `/ws/notifications`. DocumentStatusNotifier listens to all pipeline events and pushes to `/topic/documents/{id}`.
- **Spring AI Integration:** OpenAI-compatible config pointing to DashScope. `ChatClient.Builder` and `EmbeddingModel` auto-configured beans injected by adapters.
- **Agent ReAct Loop:** `AgentOrchestrator` in `rag-domain/conversation/agent/`. Planner → Executor → Evaluator → unified Rerank → Generator. Unified rerank uses canonical rewritten query (not raw user query). First-occurrence dedup via `putIfAbsent`. `RetrievalConfig` controls: `maxAgentRounds`, `maxSubQueries`, `enableFastPath`, `minSufficientChunks`, `rawScoreThreshold`. Implementations in `rag-application/agent/`.
- **Evaluator 6-Priority Early-Stop:** `LlmRetrievalEvaluator` checks in order: max-rounds force-pass → score fast-path (configurable) → empty results → LLM evaluation → degraded pass (≥3 chunks) → retry. Fast-path skips LLM when `enableFastPath=true` and score/count thresholds met.
- **Planner Robustness:** `LlmRetrievalPlanner` uses 3-step JSON extraction (direct → regex → fallback), injects conversation history, enforces `maxSubQueries` bound, injects `[Previous Attempts]` for round 2+.
- **Embedding Fallback:** `HybridRetrievalExecutor` catches embedding failures per sub-query, passes `null` vector → `LocalOpenSearchAdapter` null-guard skips KNN → BM25-only.
- **LLM HTTP Timeout:** `AgentConfig.llmReadTimeoutCustomizer` sets `RestClient` ReadTimeout via `LlmProperties.timeoutSeconds` (default 30s).
- **Generator Error Safety:** `LlmAnswerGenerator.generateStream()` has `.onErrorResume()` — SSE always terminates with `done` or `error`, never leaves frontend hanging.
- **SSE Streaming:** Chat endpoint returns `SseEmitter` with typed events (`agent_thinking`, `content_delta`, `citation`, `done`, `error`). Controller in `ChatController`, serialized via Jackson.
- **Session Persistence:** `SessionRepositoryAdapter` saves session/messages/citations to PostgreSQL via JPA. `ChatApplicationService` collects streaming results in `doOnNext` and persists on `doOnComplete`.
- **Redis Session Cache:** `ChatApplicationService` is designed to hot-cache sessions via Redis. Currently wired via Spring Data Redis (`spring.data.redis` in application-local.yml).
- **Vite Dev Proxy:** `vite.config.ts` proxies both `/api` → `:8080` and `/ws` → `:8080` (WebSocket). No CORS config needed in dev.
- **RRF Hybrid Search:** `LocalOpenSearchAdapter` executes BM25 text search and KNN vector search as two independent queries, then merges results using Reciprocal Rank Fusion (k=60). Eliminates score scale mismatch.
- **Correlation ID Tracing:** `CorrelationIdFilter` reads `X-Correlation-Id` header (or generates 8-char UUID), sets MDC `correlationId`, propagates to async threads via `TaskDecorator` in `AsyncConfig`. All logs and error responses include requestId.
- **Streaming Upload:** File upload uses `DigestInputStream` to compute SHA-256 checksum during streaming — never loads entire file into heap memory. Max 100MB per file.
- **Configurable Embedding Dimension:** `rag.services.embedding.dimension` in YAML (default 1024). `LocalOpenSearchAdapter` reads from `EmbeddingProperties`.
- **Embedding 分批：** `AliCloudEmbeddingAdapter.embedBatch()` 自动按 10 条分批 + 截断超长文本（6000 字符），适配 DashScope API 限制。
- **LLM Context 限制：** `LlmAnswerGenerator` 限制 top 8 条检索结果、每条内容截断 1500 字符，避免超过模型 context window 导致 Connection reset。

## API Convention

- All REST endpoints under `/api/v1/`
- User identity via `X-User-Id` header (auth not yet implemented)
- File upload: multipart, max 100MB
- Pagination: `?page=0&size=20&search=keyword`
- Errors: `GlobalExceptionHandler` returns `{error, message, requestId, timestamp}` — requestId is the correlation ID from `X-Correlation-Id` header or auto-generated
- Chat SSE: POST /api/v1/sessions/{id}/chat returns text/event-stream
- Session CRUD: /api/v1/spaces/{spaceId}/sessions (create, list), /api/v1/sessions/{id} (get, delete)

## Database

PostgreSQL 16 via Flyway. Migrations in `rag-boot/src/main/resources/db/migration/`.
- Tables prefixed `t_` (e.g., `t_document`, `t_user`)
- All PKs are `UUID DEFAULT gen_random_uuid()`
- `t_knowledge_space.retrieval_config` is JSONB (maxAgentRounds, chunkingStrategy, metadataExtractionPrompt, maxSubQueries, enableFastPath, minSufficientChunks, rawScoreThreshold). `SpaceMapper` provides backward-compatible defaults for old rows.

## Docker Services

| Service | Port | Image |
|---------|------|-------|
| PostgreSQL | 5432 | postgres:16 |
| Redis | 6379 | redis:7-alpine |
| OpenSearch | 9200 | opensearchproject/opensearch:2.17.0 |
| OpenSearch Dashboards | 5601 | opensearchproject/opensearch-dashboards:2.17.0 |
| Docling (doc parser) | 5001 | ghcr.io/ds4sd/docling-serve:latest |

**Docling image:** `ds4sd/docling-serve` does NOT exist on Docker Hub. The correct image is `ghcr.io/ds4sd/docling-serve:latest` (GitHub Container Registry). docker-compose.yml has been updated accordingly.

## Implementation Status

- [x] Plan 1: Project Foundation (modules, Docker, DB schema, SPI skeleton)
- [x] Plan 2: Identity & Document Management (domain models, JPA, REST APIs)
- [x] Plan 3: Document Processing Pipeline (async parsing, chunking, embedding, indexing)
- [x] Plan 4: Conversation & Agent Engine (ReAct agent, streaming, multi-turn, citations)
- [x] Plan 5: React Frontend
- [x] Agent Loop Enhancement: Planner robustness, unified rerank, evaluator early-stop, fault tolerance

Specs: `docs/superpowers/specs/2026-03-31-agentic-rag-knowledge-base-design.md`
Plans: `docs/superpowers/plans/`

## Gotchas

- `application-local.yml` contains API keys — it's gitignored. If missing, create it manually. Required keys: `spring.ai.openai.api-key`, `rag.services.{llm,embedding,rerank}.api-key` (DashScope key), `rag.services.vector-store.url`, `rag.services.doc-parser.url`, `rag.services.embedding.dimension`. See README.md Step 2 for full template.
- **Docker proxy on Windows (China network):** Docker Desktop's GUI proxy settings and transparent proxy (`http.docker.internal:3128`) are unreliable. The working solution is to write `%APPDATA%\Docker\daemon.json` directly:
  ```json
  {
    "proxies": {
      "http-proxy": "http://host.docker.internal:<VPN_PORT>",
      "https-proxy": "http://host.docker.internal:<VPN_PORT>",
      "no-proxy": "localhost,127.0.0.1"
    }
  }
  ```
  `host.docker.internal` resolves to the Windows host from inside Docker's VM. VPN proxy must be an HTTP proxy (not SOCKS-only). After writing the file, restart Docker Desktop.
- OpenSearch and docling containers are heavy. For Plan 1-2 work, only start `postgresql` and `redis`. Plan 3+ needs all services.
- `rag-adapter-outbound` depends on `rag-infrastructure` (for `ServiceRegistryConfig` properties classes). This is intentional — adapters need config to initialize.
- Flyway set to `baseline-on-migrate: true` — safe for first run on existing DB.
- JPA `ddl-auto: validate` — schema changes MUST go through Flyway migrations, not Hibernate auto-DDL.
- OpenSearch Java Client 2.17: `TermQuery.value()` requires `FieldValue.of(string)`, not raw string. KNN `vector()` takes `float[]` not `List<Float>`. No `flattened()` mapping type — use `object()`.
- `rag-application` needs `slf4j-api` and `jackson-databind` in pom.xml for event handlers (logging + JSON parsing).
- Cross-module compilation: after changing `rag-domain`, run `mvn install -pl rag-domain -DskipTests` before compiling downstream modules (`rag-application`, `rag-adapter-*`).
- **Spring AI `base-url` vs 手动 WebClient `base-url`：** `spring.ai.openai.base-url` 不带 `/v1`（Spring AI 自动拼 `/v1/chat/completions`）。`rag.services.*.base-url`（Rerank 等手动 WebClient）带 `/v1`。搞混会导致 `/v1/v1/` 重复路径 404。
- **DashScope Rerank 是原生 API，不走 OpenAI 兼容模式：** URL 为 `https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank`，请求体格式 `{model, input: {query, documents}, parameters: {top_n}}`，响应在 `output.results[]`。`AliCloudRerankAdapter` 直接硬编码此 URL。
- **DashScope Embedding 限制：** 每批 ≤10 条（`AliCloudEmbeddingAdapter.MAX_BATCH_SIZE=10`），每条 ≤8192 tokens。超长文本自动截断到 6000 字符。
- **docling-serve 0.5.x API：** 端点为 `/v1alpha/convert/file`（不是 `/v1/convert`），响应通过 `document.md_content` 返回 Markdown 字符串，按标题语义分块。
- **文档状态机 `FAILED → PARSING` 必须允许：** 否则 retry 后 `ParseEventHandler` 读到 FAILED 状态无法转换，文档永远卡死。
- **Test dependency gotcha:** `rag-domain` and `rag-adapter-outbound` need explicit test deps (`junit-jupiter`, `assertj-core`, `mockito-core`, `mockito-junit-jupiter`) in their `pom.xml`. Without them, Maven reports `Tests run: 0, BUILD SUCCESS` silently. Always verify test count > 0.
- **`rag-infrastructure` needs `spring-boot-starter-web`** for `RestClientCustomizer` and `SimpleClientHttpRequestFactory`. The base `spring-boot-starter` alone doesn't include HTTP client classes.
- **Record field additions break downstream:** Adding a field to a Java record (e.g., `EvaluationContext`) breaks all call sites. When adding fields to shared records in `rag-domain`, grep for all constructor usages across modules.
