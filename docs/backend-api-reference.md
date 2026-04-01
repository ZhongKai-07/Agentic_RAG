# Backend API & Module Reference

> Auto-generated from source code. Reflects Plan 1-4 implementation status.

---

## 1. Module Architecture

```
rag-boot                          # Spring Boot entry, Flyway migrations, YAML config
├── rag-adapter-inbound           # REST controllers, SSE, WebSocket, DTOs
│   └── rag-application           # @Service / @Component orchestration (no biz logic)
│       └── rag-domain            # Domain models, services, port interfaces (ZERO framework deps)
├── rag-adapter-outbound          # JPA entities/repos, SPI adapter implementations
│   ├── rag-domain
│   └── rag-infrastructure
└── rag-infrastructure            # ServiceRegistryConfig, AsyncConfig, OpenSearchConfig, SPI wiring
    └── rag-domain
```

---

## 2. REST API Endpoints

Base URL: `http://localhost:8080`

### 2.1 Health Check

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/health` | Returns `{status, timestamp, service}` |

### 2.2 Knowledge Space

Controller: `SpaceController` | Base: `/api/v1/spaces`

| Method | Path | Headers | Body | Response | Description |
|--------|------|---------|------|----------|-------------|
| POST | `/api/v1/spaces` | — | `CreateSpaceRequest` | `SpaceResponse` (201) | Create knowledge space |
| GET | `/api/v1/spaces` | `X-User-Id` | — | `List<SpaceResponse>` | List user's accessible spaces |
| GET | `/api/v1/spaces/{spaceId}` | — | — | `SpaceResponse` | Get space details |
| PUT | `/api/v1/spaces/{spaceId}/access-rules` | — | `UpdateAccessRulesRequest` | `SpaceResponse` | Update access rules |

**CreateSpaceRequest:**
```json
{ "name": "Compliance Q&A", "description": "...", "ownerTeam": "COB", "language": "zh", "indexName": "kb_compliance_v1" }
```

**UpdateAccessRulesRequest:**
```json
{ "rules": [{ "targetType": "BU", "targetValue": "OPS", "docSecurityClearance": "ALL" }] }
```

### 2.3 Document Management

Controller: `DocumentController` | Base: `/api/v1/spaces/{spaceId}/documents`

| Method | Path | Headers | Params / Body | Response | Description |
|--------|------|---------|---------------|----------|-------------|
| POST | `.../documents/upload` | `X-User-Id` | `file` (multipart) | `DocumentResponse` (201) | Upload single file |
| POST | `.../documents/batch-upload` | `X-User-Id` | `files` (multipart[]) | `List<DocumentResponse>` (201) | Batch upload |
| GET | `.../documents` | — | `?page=0&size=20&search=keyword` | `PageResult<DocumentResponse>` | List with pagination & search |
| GET | `.../documents/{docId}` | — | — | `DocumentDetailResponse` | Document detail |
| DELETE | `.../documents/{docId}` | — | — | 204 | Delete document + vector chunks |
| POST | `.../documents/{docId}/versions` | `X-User-Id` | `file` (multipart) | `DocumentResponse` (201) | Upload new version |
| GET | `.../documents/{docId}/versions` | — | — | `List<VersionResponse>` | Version history |
| POST | `.../documents/{docId}/retry` | — | — | `DocumentResponse` | Retry failed parsing |
| PUT | `.../documents/batch-tags` | — | `BatchUpdateTagsRequest` | 200 | Batch add/remove tags |
| DELETE | `.../documents/batch-delete` | — | `BatchDeleteRequest` | 204 | Batch delete |

### 2.4 Chat & Conversation

Controller: `ChatController` | Base: `/api/v1`

| Method | Path | Headers | Body | Response | Description |
|--------|------|---------|------|----------|-------------|
| POST | `/api/v1/spaces/{spaceId}/sessions` | `X-User-Id` | `CreateSessionRequest` (optional) | `SessionResponse` (201) | Create chat session |
| GET | `/api/v1/spaces/{spaceId}/sessions` | `X-User-Id` | — | `List<SessionResponse>` | List user's sessions |
| GET | `/api/v1/sessions/{sessionId}` | — | — | `SessionDetailResponse` | Session with full message history |
| DELETE | `/api/v1/sessions/{sessionId}` | — | — | 204 | Delete session |
| **POST** | **`/api/v1/sessions/{sessionId}/chat`** | `X-User-Id` | `ChatRequest` | **SSE Stream** | **Send message, stream response** |

**ChatRequest:**
```json
{ "message": "FICC account opening requires what materials?" }
```

**SSE Stream Events** (`text/event-stream`):

```
event: agent_thinking
data: {"round":1,"content":"Analyzing query..."}

event: agent_searching
data: {"round":1,"queries":["FICC AML account opening materials"]}

event: agent_evaluating
data: {"round":1,"sufficient":false}

event: agent_searching
data: {"round":2,"queries":["investor classification due diligence"]}

event: agent_evaluating
data: {"round":2,"sufficient":true}

event: content_delta
data: {"delta":"According to"}

event: content_delta
data: {"delta":" the AML Manual"}

event: citation
data: {"citationIndex":1,"documentId":"xxx","documentTitle":"AML Manual","chunkId":"...","pageNumber":12,"sectionPath":"3.2","snippet":"..."}

event: content_delta
data: {"delta":"[1], FICC account opening requires..."}

event: done
data: {"messageId":"xxx","totalCitations":2}
```

Error event:
```
event: error
data: {"code":"AGENT_ERROR","message":"..."}
```

### 2.5 User

Controller: `UserController` | Base: `/api/v1/users`

| Method | Path | Headers | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/v1/users/me` | `X-User-Id` | `UserResponse` | Current user profile |

### 2.6 WebSocket (Document Status)

| Protocol | Endpoint | Topic | Description |
|----------|----------|-------|-------------|
| STOMP over SockJS | `ws://localhost:8080/ws/notifications` | `/topic/documents/{documentId}` | Real-time document processing status |

**Payload:**
```json
{
  "type": "DOCUMENT_STATUS_CHANGED",
  "payload": {
    "documentId": "uuid",
    "status": "UPLOADED | PARSED | INDEXED",
    "progress": 0-100,
    "message": "description"
  }
}
```

---

## 3. Application Layer Components

### 3.1 Application Services (@Service)

| Class | Package | Responsibility |
|-------|---------|----------------|
| `ChatApplicationService` | `application.chat` | Session CRUD, agent orchestration, streaming chat, message persistence |
| `DocumentApplicationService` | `application.document` | Document upload/version/delete, publishes `DocumentUploadedEvent` |
| `SpaceApplicationService` | `application.identity` | Space CRUD, access rule management |

### 3.2 Event Handlers (@Component, @Async)

| Class | Listens To | Publishes | Description |
|-------|------------|-----------|-------------|
| `ParseEventHandler` | `DocumentUploadedEvent` | `DocumentParsedEvent` | Calls DocParserPort (Docling), semantic chunking |
| `IndexEventHandler` | `DocumentParsedEvent` | `ChunksIndexedEvent` | LLM metadata extraction, batch embedding, OpenSearch upsert |

### 3.3 Agent Components (@Component)

| Class | Interface | Description |
|-------|-----------|-------------|
| `LlmRetrievalPlanner` | `RetrievalPlanner` | LLM-based query rewrite & decomposition into sub-queries |
| `HybridRetrievalExecutor` | `RetrievalExecutor` | Embed query + OpenSearch hybrid search (BM25 + KNN) |
| `LlmRetrievalEvaluator` | `RetrievalEvaluator` | LLM judges if retrieved results sufficiently answer the question |
| `LlmAnswerGenerator` | `AnswerGenerator` | Streaming LLM answer with citation extraction |

---

## 4. Domain Layer (rag-domain)

### 4.1 Bounded Contexts

| Context | Package | Aggregate Root | Key Models |
|---------|---------|----------------|------------|
| Identity | `domain.identity` | User, KnowledgeSpace | AccessRule, RetrievalConfig, Role, TargetType |
| Document | `domain.document` | Document | DocumentVersion, DocumentStatus, FileType |
| Knowledge | `domain.knowledge` | KnowledgeChunk | — |
| Conversation | `domain.conversation` | ChatSession | Message, Citation, AgentTrace, StreamEvent |

### 4.2 Port Interfaces (SPI)

| Port | Package | Methods | local Adapter | aws Adapter |
|------|---------|---------|---------------|-------------|
| `LlmPort` | `conversation.port` | `chat()`, `streamChat()` | `AliCloudLlmAdapter` | `GatewayLlmAdapter` (stub) |
| `EmbeddingPort` | `knowledge.port` | `embed()`, `embedBatch()` | `AliCloudEmbeddingAdapter` | `GatewayEmbeddingAdapter` (stub) |
| `RerankPort` | `knowledge.port` | `rerank()` | `AliCloudRerankAdapter` | `GatewayRerankAdapter` (stub) |
| `VectorStorePort` | `knowledge.port` | `upsertChunks()`, `deleteByDocumentId()`, `hybridSearch()` | `LocalOpenSearchAdapter` | `AwsOpenSearchAdapter` (stub) |
| `DocParserPort` | `document.port` | `parse()` | `DoclingJavaAdapter` | `AwsBedrockDocAdapter` (stub) |
| `FileStoragePort` | `document.port` | `store()`, `retrieve()`, `delete()` | `LocalFileStorageAdapter` | `S3FileStorageAdapter` (stub) |
| `DocumentRepository` | `document.port` | CRUD + pagination + tags | `DocumentRepositoryAdapter` | — |
| `SpaceRepository` | `identity.port` | CRUD + access rules | `SpaceRepositoryAdapter` | — |
| `UserRepository` | `identity.port` | CRUD | `UserRepositoryAdapter` | — |
| `SessionRepository` | `conversation.port` | CRUD + messages + citations | `SessionRepositoryAdapter` | — |

### 4.3 Domain Services

| Class | Package | Description |
|-------|---------|-------------|
| `ChatService` | `conversation.service` | Session lifecycle, message creation, validation |
| `AgentOrchestrator` | `conversation.agent` | ReAct loop: Plan → Execute → Evaluate → Generate |
| `DocumentLifecycleService` | `document.service` | Document/version creation, checksum |
| `KnowledgeIndexService` | `knowledge.service` | Builds ChunkDocument with embeddings + metadata |
| `SpaceAuthorizationService` | `identity.service` | Resolves user security clearance from access rules |

### 4.4 Domain Events

```
DocumentUploadedEvent  ──► ParseEventHandler  ──► DocumentParsedEvent
                                                       │
                                                       ▼
ChunksIndexedEvent  ◄──  IndexEventHandler  ◄──────────┘
       │
       ▼
DocumentStatusNotifier (WebSocket push)
```

---

## 5. Outbound Adapters (rag-adapter-outbound)

### 5.1 Persistence (JPA)

| Entity | Table | Key Fields |
|--------|-------|------------|
| `DocumentEntity` | `t_document` | documentId, spaceId, title, status, chunkCount |
| `DocumentVersionEntity` | `t_document_version` | versionId, documentId, versionNo, filePath |
| `DocumentTagEntity` | `t_document_tag` | tagId, documentId, tagName |
| `KnowledgeSpaceEntity` | `t_knowledge_space` | spaceId, name, indexName, retrievalConfig (JSONB) |
| `AccessRuleEntity` | `t_access_rule` | ruleId, spaceId, targetType, targetValue |
| `SpacePermissionEntity` | `t_space_permission` | permissionId, userId, spaceId, accessLevel |
| `UserEntity` | `t_user` | userId, username, bu, team, role |
| `ChatSessionEntity` | `t_chat_session` | sessionId, userId, spaceId, title, status |
| `MessageEntity` | `t_message` | messageId, sessionId, role, content, agentTrace (JSONB) |
| `CitationEntity` | `t_citation` | citationId, messageId, documentId, chunkId, snippet |

### 5.2 Infrastructure Configs

| Class | Bean(s) | Description |
|-------|---------|-------------|
| `ServiceRegistryConfig` | LlmProperties, EmbeddingProperties, RerankProperties, VectorStoreProperties, DocParserProperties, FileStorageProperties | Unified `rag.services.*` config entry |
| `AsyncConfig` | `documentProcessingExecutor` | ThreadPool: core=2, max=5, queue=50 |
| `OpenSearchConfig` | `OpenSearchClient` | OpenSearch Java Client 2.17 |
| `AgentConfig` | `AgentOrchestrator` | Wires Planner/Executor/Evaluator/Generator/RerankPort |
| `SpiAutoConfiguration` | — | Component scan for `com.rag.adapter.outbound` |
| `WebSocketConfig` | — | STOMP broker `/topic`, endpoint `/ws/notifications` |

---

## 6. Error Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`):

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `IllegalArgumentException` | 404 | `NOT_FOUND` |
| `IllegalStateException` | 409 | `CONFLICT` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `MaxUploadSizeExceededException` | 413 | `FILE_TOO_LARGE` |
| Other | 500 | `INTERNAL_ERROR` |

Response format:
```json
{ "error": "NOT_FOUND", "message": "Document not found: uuid", "timestamp": "2026-04-01T12:00:00Z" }
```

---

## 7. Authentication

Currently **not enforced**. User identity passed via `X-User-Id` header (UUID). All endpoints that require user context expect this header.
