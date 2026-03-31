# Agentic RAG Knowledge Base

Enterprise RAG Q&A chatbot with agentic retrieval, multi-turn conversation, and streaming responses.

**Stack:** Java 21 · Spring Boot 3.4.5 · Spring AI 1.0.0 · PostgreSQL 16 · OpenSearch 2.17 · Maven multi-module

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

## API Convention

- All REST endpoints under `/api/v1/`
- User identity via `X-User-Id` header (auth not yet implemented)
- File upload: multipart, max 100MB
- Pagination: `?page=0&size=20&search=keyword`
- Errors: `GlobalExceptionHandler` returns `{error, message, timestamp}`

## Database

PostgreSQL 16 via Flyway. Migrations in `rag-boot/src/main/resources/db/migration/`.
- Tables prefixed `t_` (e.g., `t_document`, `t_user`)
- All PKs are `UUID DEFAULT gen_random_uuid()`
- `t_knowledge_space.retrieval_config` is JSONB (maxAgentRounds, chunkingStrategy, metadataExtractionPrompt)

## Docker Services

| Service | Port | Image |
|---------|------|-------|
| PostgreSQL | 5432 | postgres:16 |
| Redis | 6379 | redis:7-alpine |
| OpenSearch | 9200 | opensearchproject/opensearch:2.17.0 |
| OpenSearch Dashboards | 5601 | opensearchproject/opensearch-dashboards:2.17.0 |
| Docling (doc parser) | 5001 | ds4sd/docling-serve:latest |

## Implementation Status

- [x] Plan 1: Project Foundation (modules, Docker, DB schema, SPI skeleton)
- [x] Plan 2: Identity & Document Management (domain models, JPA, REST APIs)
- [x] Plan 3: Document Processing Pipeline (async parsing, chunking, embedding, indexing)
- [ ] Plan 4: Conversation & Agent Engine (ReAct agent, streaming, multi-turn, citations)
- [ ] Plan 5: React Frontend

Specs: `docs/superpowers/specs/2026-03-31-agentic-rag-knowledge-base-design.md`
Plans: `docs/superpowers/plans/`

## Gotchas

- `application-local.yml` contains API keys — it's gitignored. Copy from template if missing.
- OpenSearch and docling containers are heavy. For Plan 1-2 work, only start `postgresql` and `redis`. Plan 3+ needs all services.
- `rag-adapter-outbound` depends on `rag-infrastructure` (for `ServiceRegistryConfig` properties classes). This is intentional — adapters need config to initialize.
- Flyway set to `baseline-on-migrate: true` — safe for first run on existing DB.
- JPA `ddl-auto: validate` — schema changes MUST go through Flyway migrations, not Hibernate auto-DDL.
- OpenSearch Java Client 2.17: `TermQuery.value()` requires `FieldValue.of(string)`, not raw string. KNN `vector()` takes `float[]` not `List<Float>`. No `flattened()` mapping type — use `object()`.
- `rag-application` needs `slf4j-api` and `jackson-databind` in pom.xml for event handlers (logging + JSON parsing).
- Cross-module compilation: after changing `rag-domain`, run `mvn install -pl rag-domain -DskipTests` before compiling downstream modules (`rag-application`, `rag-adapter-*`).
