# Agentic RAG - Development Handoff Document

> Last updated: 2026-04-01
> Branch: `feature/plan4-code-review-and-frontend`

---

## 1. Project Overview

**Agentic RAG Knowledge Base** is an enterprise-grade RAG chatbot system that enables knowledge workers to upload department knowledge files and conduct intelligent multi-turn Q&A conversations with agentic retrieval capabilities.

| Layer | Technology |
|-------|-----------|
| Language/Framework | Java 21, Spring Boot 3.4.5, Spring AI 1.0.0 |
| Build | Maven 3.9+ (multi-module) |
| Database | PostgreSQL 16 (Flyway migrations) |
| Vector DB | OpenSearch 2.17 |
| Cache | Redis 7 |
| AI Services | AliCloud DashScope (local profile) |
| Document Parsing | Docling (REST, `ghcr.io/ds4sd/docling-serve:latest`) |
| Frontend | React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui |
| Architecture | DDD Hexagonal + CQRS + Event-Driven |

---

## 2. Module Structure

```
Agentic_RAG/
├── rag-boot/              # Spring Boot entry point, Flyway migrations, YAML configs
├── rag-domain/            # Pure domain models, ports, services (NO Spring deps)
├── rag-application/       # Command/Query handlers, event handlers, agent implementations
├── rag-adapter-inbound/   # REST controllers, SSE, WebSocket, DTOs
├── rag-adapter-outbound/  # JPA persistence, SPI implementations (LLM, embedding, etc.)
├── rag-infrastructure/    # Configuration, bean wiring, SPI auto-discovery
├── rag-frontend/          # React SPA (Vite dev server on :3000, proxies to :8080)
└── docker/                # docker-compose.yml for local infrastructure
```

**Dependency direction:**

```
rag-boot
├── rag-adapter-inbound → rag-application → rag-domain
├── rag-adapter-outbound → rag-domain + rag-infrastructure
└── rag-infrastructure → rag-domain
```

**Critical constraint:** `rag-domain` must NOT import Spring, JPA, or any framework dependencies. Only allowed: `reactor-core` (for `Flux`) and `jakarta.persistence` annotations.

---

## 3. Domain Bounded Contexts

### Identity (`domain.identity`)
- **Aggregates:** User, KnowledgeSpace
- **Models:** Role, AccessRule, TargetType (BU/TEAM/USER), SecurityLevel (ALL/MANAGEMENT), RetrievalConfig
- **Service:** SpaceAuthorizationService

### Document (`domain.document`)
- **Aggregate:** Document (contains DocumentVersion list)
- **State machine:** `UPLOADED → PARSING → PARSED → INDEXING → INDEXED` (or `FAILED`)
- **Events:** DocumentUploadedEvent → DocumentParsedEvent → ChunksIndexedEvent
- **Service:** DocumentLifecycleService

### Knowledge (`domain.knowledge`)
- **Aggregate:** KnowledgeChunk
- **Service:** KnowledgeIndexService
- **Ports:** VectorStorePort (hybrid BM25 + KNN search), EmbeddingPort, RerankPort

### Conversation (`domain.conversation`)
- **Aggregate:** ChatSession (contains Message list)
- **Models:** Citation, AgentTrace, StreamEvent (sealed interface: AgentThinking, AgentSearching, AgentEvaluating, ContentDelta, CitationEmit, Done, Error)
- **Agent abstractions:** RetrievalPlanner, RetrievalExecutor, RetrievalEvaluator, AnswerGenerator, AgentOrchestrator (ReAct loop)
- **Service:** ChatService

---

## 4. Key Architecture Patterns

### SPI Pluggable Adapters (switch via Spring Profile)

| Port | `local` Profile | `aws` Profile (stub) |
|------|-----------------|----------------------|
| LlmPort | AliCloudLlmAdapter (DashScope qwen-plus) | GatewayLlmAdapter |
| EmbeddingPort | AliCloudEmbeddingAdapter (text-embedding-v3) | GatewayEmbeddingAdapter |
| RerankPort | AliCloudRerankAdapter (gte-rerank) | GatewayRerankAdapter |
| VectorStorePort | LocalOpenSearchAdapter | AwsOpenSearchAdapter |
| DocParserPort | DoclingJavaAdapter | AwsBedrockDocAdapter |
| FileStoragePort | LocalFileStorageAdapter (`./uploads`) | S3FileStorageAdapter |

### Async Event-Driven Document Pipeline

```
Upload (DocumentController)
  → publish DocumentUploadedEvent
  → ParseEventHandler (@Async) → DocParserPort.parse() → publish DocumentParsedEvent
  → IndexEventHandler (@Async) → LLM metadata → embed → index to OpenSearch → publish ChunksIndexedEvent
  → DocumentStatusNotifier → WebSocket push to /topic/documents/{id}
```

### ReAct Agent Loop

```
AgentOrchestrator.orchestrate()
  ├─ RetrievalPlanner.plan()        [LLM query rewriting/decomposition]
  ├─ RetrievalExecutor.execute()    [embed, hybrid search, rerank]
  ├─ RetrievalEvaluator.evaluate()  [LLM sufficiency check]
  ├─ if sufficient → AnswerGenerator.generate() [streaming response with citations]
  ├─ else → repeat (max rounds: RetrievalConfig.maxAgentRounds)
  └─ yields: Flux<StreamEvent>
```

### SSE Streaming Chat

POST `/api/v1/sessions/{id}/chat` returns `text/event-stream` with typed events:
- `agent_thinking` — round number, thought text
- `agent_searching` — sub-queries being executed
- `agent_evaluating` — sufficiency check result
- `content_delta` — incremental answer text
- `citation` — citation reference (flat JSON, unwrapped from CitationEmit)
- `done` — final message ID
- `error` — error message

---

## 5. API Endpoints Summary

All endpoints under `/api/v1/` with `X-User-Id: <uuid>` header.

### Spaces
| Method | Path | Description |
|--------|------|-------------|
| POST | `/spaces` | Create space |
| GET | `/spaces` | List accessible spaces |
| GET | `/spaces/{spaceId}` | Get space detail |
| PUT | `/spaces/{spaceId}/access-rules` | Update access rules |

### Documents
| Method | Path | Description |
|--------|------|-------------|
| POST | `/spaces/{spaceId}/documents/upload` | Upload single file (multipart) |
| POST | `/spaces/{spaceId}/documents/batch-upload` | Upload multiple files |
| GET | `/spaces/{spaceId}/documents` | List (paginated, searchable) |
| GET | `/spaces/{spaceId}/documents/{docId}` | Get detail with versions |
| DELETE | `/spaces/{spaceId}/documents/{docId}` | Delete (cleans files + vectors) |
| POST | `/spaces/{spaceId}/documents/{docId}/versions` | Upload new version |
| GET | `/spaces/{spaceId}/documents/{docId}/versions` | List version history |
| POST | `/spaces/{spaceId}/documents/{docId}/retry` | Retry failed parsing |
| PUT | `/documents/batch-tags` | Batch update tags |
| DELETE | `/documents/batch-delete` | Batch delete |

### Chat
| Method | Path | Description |
|--------|------|-------------|
| POST | `/spaces/{spaceId}/sessions` | Create session |
| GET | `/spaces/{spaceId}/sessions` | List sessions |
| GET | `/sessions/{sessionId}` | Get session with messages |
| DELETE | `/sessions/{sessionId}` | Delete session |
| POST | `/sessions/{sessionId}/chat` | **SSE streaming chat** |

### Other
| Method | Path | Description |
|--------|------|-------------|
| GET | `/users/me` | Current user info |
| GET | `/health` | Health check |
| WS | `/ws/notifications` | STOMP/SockJS for document status |

---

## 6. Database Schema (PostgreSQL 16)

10 tables managed by Flyway (`V1__initial_schema.sql`), all UUID PKs:

1. `t_user` — userId, username, bu, team, role, status
2. `t_knowledge_space` — spaceId, name, indexName, ownerTeam, language, retrievalConfig (JSONB)
3. `t_access_rule` — ruleId, spaceId (FK), targetType, targetValue, docSecurityClearance
4. `t_space_permission` — permissionId, userId (FK), spaceId (FK), accessLevel
5. `t_document` — documentId, spaceId (FK), title, status, chunkCount, currentVersionId (FK)
6. `t_document_version` — versionId, documentId (FK), versionNo, filePath, createdAt
7. `t_document_tag` — tagId, documentId (FK), tagName
8. `t_chat_session` — sessionId, userId (FK), spaceId (FK), title, status, createdAt
9. `t_message` — messageId, sessionId (FK), role, content, agentTrace (JSONB), createdAt
10. `t_citation` — citationId, messageId (FK), documentId (FK), chunkId, snippet, pageNumber

Schema managed by Flyway (`baseline-on-migrate: true`). JPA set to `ddl-auto: validate` — all changes via migrations.

---

## 7. Docker Infrastructure

```bash
cd docker && docker compose up -d
```

| Service | Port | Image | Notes |
|---------|------|-------|-------|
| PostgreSQL | 5432 | postgres:16 | DB: rag_db, User: rag_user/rag_password |
| Redis | 6379 | redis:7-alpine | 256MB max |
| OpenSearch | 9200 | opensearchproject/opensearch:2.17.0 | Single-node, security disabled |
| OpenSearch Dashboards | 5601 | opensearchproject/opensearch-dashboards:2.17.0 | Web UI |
| Docling | 5001 | ghcr.io/ds4sd/docling-serve:latest | 4GB memory, **NOT on Docker Hub** |

**Windows Docker proxy:** If behind VPN, edit `%APPDATA%\Docker\daemon.json` with proxy settings (not Docker GUI).

---

## 8. Frontend Architecture

### Tech Stack
- React 18 + TypeScript 5.5 + Vite 5.4
- Tailwind CSS 3.4 + shadcn/ui (Radix primitives)
- Zustand 4.5 (state management)
- SockJS + STOMP (WebSocket)
- Lucide React (icons)

### Pages
- **LoginPage** — Mock auth with UUID input (no real auth yet)
- **ChatPage** — 3-column: sessions | messages | citations, SSE streaming
- **DocumentsPage** — Table with upload, batch ops, pagination, search, real-time status via WebSocket
- **SpacesPage** — CRUD spaces with access rule editor

### State Stores (Zustand)
- `useAuthStore` — userId + user (persisted to localStorage)
- `useSpaceStore` — spaces list + currentSpaceId (persisted)
- `useChatStore` — sessions, messages, streaming state, agent status (memory only)
- `useDocumentStore` — documents, pagination, selection (memory only)

### Real-time Communication
- **SSE** (`useSSE` hook) — Chat streaming via manual EventSource parsing
- **WebSocket** (`useDocumentNotification` hook) — Document status updates via STOMP

### Dev Server
- Port 3000, proxies `/api` → `:8080` and `/ws` → `:8080`
- Path alias: `@/` → `./src`

---

## 9. Setup on New Desktop

### Prerequisites
- Java 21 (JDK)
- Maven 3.9+
- Node.js 18+ / npm
- Docker Desktop
- Git

### Steps

```bash
# 1. Clone and checkout
git clone https://github.com/ZhongKai-07/Agentic_RAG.git
cd Agentic_RAG
git checkout feature/plan4-code-review-and-frontend

# 2. Start infrastructure
cd docker && docker compose up -d
cd ..

# 3. Build backend
mvn clean install -DskipTests

# 4. Run backend (local profile)
mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=local
# App runs on http://localhost:8080

# 5. Setup frontend (in a separate terminal)
cd rag-frontend
npm install
npm run dev
# Dev server on http://localhost:3000

# 6. Verify
curl http://localhost:8080/api/v1/health
# Open http://localhost:3000 in browser
```

### Config Notes
- DashScope API key is in `rag-boot/src/main/resources/application-local.yml`
- If key is expired, replace `sk-ccf44e34ec0f42e98e0bafde3efe7e50` with a new DashScope key
- After changing `rag-domain`, rebuild: `mvn install -pl rag-domain -DskipTests`

---

## 10. Known Issues & P0-P2 Status

### Code Review Fixes Applied (8 items, all compiled, need manual verification)

| # | Priority | Issue | Fix Status | Verified? |
|---|----------|-------|------------|-----------|
| 1 | P0 | Permission boundaries not enforced on endpoints | Fixed — all controllers now read X-User-Id | Not yet |
| 2 | P0 | New space invisible to creator (no AccessRule created) | Fixed — auto-creates AccessRule on creation | Not yet |
| 3 | P0 | First document upload fails (delete on non-existent index) | Fixed — checks index existence before delete | Not yet |
| 4 | P0 | SSE citation protocol mismatch (nested vs flat JSON) | Fixed — unwraps CitationEmit to flat Citation | Not yet |
| 5 | P1 | File stream leak in ParseEventHandler | Fixed — try-with-resources | Not yet |
| 6 | P1 | Document deletion leaves orphan files (historical versions) | Fixed — deletes all version files + vector chunks | Not yet |
| 7 | P1 | SSE stream not disposed on client disconnect | Fixed — onCompletion/onTimeout dispose upstream | Not yet |
| 8 | P2 | Agent planner/evaluator too optimistic on failure | Fixed — graceful fallback to original query | Not yet |

### Known Bug (Unresolved)

**NullPointerException in DocumentMapper.toVersionEntity** on document upload:
- `DocumentVersion.versionId()` throws NPE because `v` is null
- Location: `DocumentRepositoryAdapter.saveVersion()`
- Root cause: likely race condition or missing version initialization in `DocumentApplicationService.uploadDocument()`
- Status: **Needs investigation**

### Frontend Issues
- Missing font file: `jetbrains-mono-400.woff2` still referenced but absent
- Some UI garbled characters in: SessionList, StatusBadge, SpacesPage (encoding issue)

---

## 11. What Still Needs To Be Done

### Immediate (Before Integration Testing)
- [ ] Manually verify all 8 code review fixes (see table above)
- [ ] Fix the NullPointerException in DocumentMapper
- [ ] Fix frontend font/encoding issues
- [ ] End-to-end test: upload doc → wait for INDEXED → ask question → get streamed answer with citations

### Short-Term
- [ ] Add automated tests (currently **zero tests** across all modules)
  - API integration tests for permission validation
  - Agent fallback behavior tests
  - SSE streaming contract tests
  - Document pipeline integration tests
- [ ] Replace mock auth (UUID input) with real authentication (OAuth/JWT)
- [ ] Add global error handling in frontend (toast notifications, error boundary)

### Medium-Term
- [ ] Implement AWS profile adapters for production deployment
- [ ] Add rate limiting and audit logging
- [ ] Performance test document processing pipeline at scale
- [ ] CI/CD pipeline setup

---

## 12. Development Gotchas

1. **Docling image:** Use `ghcr.io/ds4sd/docling-serve:latest` (GitHub Container Registry), NOT Docker Hub
2. **OpenSearch 2.17 API:** `TermQuery.value()` requires `FieldValue.of(string)`, KNN vector takes `float[]`, no `flattened()` type
3. **Flyway:** `baseline-on-migrate: true`, never use `ddl-auto: update` — all schema changes via migration files
4. **Spring AI:** Uses OpenAI-compatible config pointing to DashScope — don't confuse with actual OpenAI
5. **Domain purity:** If you add a dependency to `rag-domain/pom.xml`, make sure it's NOT a Spring/JPA dependency
6. **Windows line endings:** Git warnings about LF→CRLF are cosmetic, can be ignored
7. **Frontend proxy:** API calls only work through Vite dev server (:3000) which proxies to :8080 — don't open :8080 directly for the frontend

---

## 13. Key Files Quick Reference

| Purpose | Path |
|---------|------|
| Project conventions | `CLAUDE.md` |
| DB schema | `rag-boot/src/main/resources/db/migration/V1__initial_schema.sql` |
| Local config | `rag-boot/src/main/resources/application-local.yml` |
| Docker setup | `docker/docker-compose.yml` |
| REST controllers | `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/` |
| Agent logic | `rag-application/src/main/java/com/rag/application/agent/` |
| Event handlers | `rag-application/src/main/java/com/rag/application/event/` |
| Domain models | `rag-domain/src/main/java/com/rag/domain/` |
| JPA entities | `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/` |
| Frontend API client | `rag-frontend/src/api/` |
| Frontend stores | `rag-frontend/src/stores/` |
| Code review details | `docs/code_review.md/` |
| Error log | `error_log/error_log.md` |
| API reference | `docs/backend-api-reference.md` |
