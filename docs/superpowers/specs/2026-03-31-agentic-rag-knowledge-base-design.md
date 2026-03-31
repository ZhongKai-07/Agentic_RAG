# Agentic RAG Knowledge Base Chatbot - Design Spec

> **Date**: 2026-03-31
> **Status**: Draft
> **Author**: AI Architect

---

## 1. Overview

### 1.1 Business Goal

Build an enterprise-grade department RAG knowledge base Q&A chatbot that supports business users uploading and managing department knowledge files, and provides intelligent retrieval-augmented Q&A with agentic capabilities, multi-turn conversation, and streaming responses.

### 1.2 Business Requirements

**Requirement 1: OPS/COB Compliance Q&A (Chinese)**

- Users: EST/EQD/FICC internal staff + OPS COB team; ~100 queries/day
- Content: AML/KYC policies, operating manuals; PDF/Word/Excel (no images in Phase 1)
- Permissions: Any user can access all source files in Phase 1; design must reserve technical path for future multi-tenant/tiered access
- Key Challenge: Structured interpretation of Chinese policy clauses by dimensions like "customer type / investor classification / product application"

**Requirement 2: Collateral Intelligent Screening (English)**

- Users: OPS Collateral team; ~10-20 queries/day
- Content: ISDA/CSA/GMRA agreements and process files; ~150 agreements, 1.57GB, primarily English PDF
- Key Challenge: Rapid extraction of parameters (Party A/B, MTA, IA, margin flow, linked accounts) from agreements; sensitive to evidence citation, versioning, and audit

### 1.3 Key Architectural Constraints

| Constraint | Description |
|------------|-------------|
| **Pluggable Services** | Local dev uses Alibaba Cloud APIs + docling-java + local OpenSearch; production migrates to company LLM gateway + AWS Bedrock Data Automation + AWS OpenSearch. Switch via Spring Profile, zero code change. |
| **DDD** | Domain-Driven Design with bounded contexts |
| **CQRS + Event-Driven** | Command/Query separation; async file processing via domain events |
| **Hexagonal Architecture** | Domain core has zero external dependencies; all integrations via SPI ports |
| **Agent-Ready** | Phase 1: single ReAct agent with smart retrieval; Phase 2: multi-agent collaboration with Supervisor routing |

---

## 2. Architecture

### 2.1 Approach: DDD Hexagonal + CQRS + Event-Driven

```
Command Side: Upload/manage files → Domain Events → Async parsing → Write to vector store
Query Side:   Chat retrieval → Agent ReAct loop → Streaming response
```

Pragmatic simplification: No full Event Sourcing. Use Spring ApplicationEvent for in-process event-driven communication.

### 2.2 Bounded Contexts

```
┌─────────────────────────────────────────────────────────┐
│                    API Gateway Layer                      │
│              (Unified config entry / Auth)                │
└──────┬──────────┬──────────────┬───────────────┬─────────┘
       │          │              │               │
  ┌────▼───┐ ┌───▼────┐  ┌─────▼──────┐  ┌─────▼──────┐
  │Identity│ │Document│  │ Knowledge  │  │Conversation│
  │Context │ │Context │  │  Context   │  │  Context   │
  │        │ │        │  │            │  │            │
  │·User   │ │·Upload │  │·Knowledge  │  │·Session    │
  │·Perm   │ │·Version│  │ Space      │  │·Agent      │
  │·Space  │ │·Async  │  │·Vector     │  │·Multi-turn │
  │ AuthZ  │ │ Parse  │  │ Search     │  │·Streaming  │
  └────────┘ └───┬────┘  └─────▲──────┘  └────────────┘
                 │             │
                 └─────────────┘
              DocumentParsedEvent
```

| Bounded Context | Aggregate Root | Responsibilities |
|-----------------|---------------|------------------|
| **Identity** | User, KnowledgeSpace | User management, BU/Team/Role, knowledge space creation & authorization |
| **Document** | Document (with DocumentVersion) | File CRUD, version management, batch operations, trigger async parsing |
| **Knowledge** | KnowledgeChunk | Chunk storage & indexing, vector search, rerank |
| **Conversation** | ChatSession (with Message) | Session lifecycle, Agent ReAct loop, streaming output, citation tracing |

**Inter-context Communication:**

- Document → Knowledge: via `DocumentParsedEvent` (async, chunks written to vector store)
- Conversation → Knowledge: synchronous call (Agent retrieval)
- Conversation → Identity: auth check (get user's accessible spaces)
- Document → Identity: auth check (validate upload/manage permissions)

### 2.3 Hexagonal Architecture + SPI Pluggable Layer

```
                    ┌─────────────────────────┐
  Inbound           │      Domain Core        │         Outbound
  Adapters          │                         │         Adapters (SPI)
                    │                         │
 ┌──────────┐       │  ┌─────────────────┐    │      ┌──────────────────┐
 │ REST API │──────▶│  │  Domain Model   │    │      │ LlmPort          │
 └──────────┘       │  │  Domain Service │    │──▶   │  ├─ AliCloudLlm  │
 ┌──────────┐       │  │  Domain Event   │    │      │  └─ GatewayLlm   │
 │   SSE    │◀──────│  │  Agent Engine   │    │      ├──────────────────┤
 └──────────┘       │  └─────────────────┘    │      │ VectorStorePort  │
 ┌──────────┐       │                         │      │  ├─ LocalOS      │
 │WebSocket │◀─────▶│  ┌─────────────────┐    │      │  └─ AwsOS        │
 └──────────┘       │  │   Port Interfaces│───│──▶   ├──────────────────┤
                    │  │ (Inbound/Outbound)   │      │ DocParserPort    │
                    │  └─────────────────┘    │      │  ├─ DoclingJava  │
                    │                         │      │  └─ AwsBedrock   │
                    └─────────────────────────┘      ├──────────────────┤
                                                     │ EmbeddingPort    │
                                                     │  ├─ AliCloudEmb  │
                                                     │  └─ GatewayEmb   │
                                                     ├──────────────────┤
                                                     │ RerankPort       │
                                                     │  ├─ AliCloudRR   │
                                                     │  └─ GatewayRR    │
                                                     ├──────────────────┤
                                                     │ FileStoragePort  │
                                                     │  ├─ LocalFS      │
                                                     │  └─ S3           │
                                                     └──────────────────┘
```

**SPI Switch Overview:**

| SPI Port | local Profile | aws Profile |
|----------|---------------|-------------|
| LlmPort | AliCloudLlmAdapter | GatewayLlmAdapter |
| EmbeddingPort | AliCloudEmbeddingAdapter | GatewayEmbeddingAdapter |
| RerankPort | AliCloudRerankAdapter | GatewayRerankAdapter |
| VectorStorePort | LocalOpenSearchAdapter | AwsOpenSearchAdapter |
| DocParserPort | DoclingJavaAdapter | AwsBedrockDocAdapter |
| FileStoragePort | LocalFileStorageAdapter | S3FileStorageAdapter |

Switch environment: `--spring.profiles.active=aws`, zero code change.

---

## 3. Permission Model

### 3.1 Three-Dimension Permission Design

Knowledge producers and consumers are different groups. For example, OPS COB team maintains compliance knowledge base, but users include COB + FICC + EST + EQD.

**Core Concept: Knowledge Space**

```
Knowledge Space
  ├── Maintained by: a specific Team (owner)
  ├── Access Rules: [BU/Team/User list] — who can query
  ├── Doc Security Level: per-document visibility control
  └── OpenSearch Index: physical storage
```

### 3.2 Access Resolution Flow

```
1. Lookup user's accessible knowledge spaces → [compliance, collateral]
2. User selects / system routes to target space
3. Within that space's index, filter by security_level
```

### 3.3 Permission Dimensions

| Dimension | Cardinality | Isolation | Description |
|-----------|------------|-----------|-------------|
| Knowledge Space | Low | Physical (independent OpenSearch index) | `kb_compliance_v1`, `kb_collateral_v1` |
| Team/BU Access | Low | Logical (AccessRule table) | Cross-BU authorization via rules |
| Doc Security Level | Binary | Logical (query-time filter) | `ALL` (everyone), `MANAGEMENT` (managers only) |

### 3.4 Access Rule Model

```json
{
  "space": "compliance",
  "access_rules": [
    { "target_type": "BU",   "target_value": "OPS",  "clearance": "ALL" },
    { "target_type": "BU",   "target_value": "FICC", "clearance": "ALL" },
    { "target_type": "BU",   "target_value": "EST",  "clearance": "ALL" },
    { "target_type": "BU",   "target_value": "EQD",  "clearance": "ALL" },
    { "target_type": "TEAM", "target_value": "COB",  "clearance": "MANAGEMENT" }
  ]
}
```

---

## 4. Domain Models

### 4.1 Identity Context

```java
User
  ├── userId: UUID
  ├── username: String
  ├── displayName: String
  ├── email: String
  ├── bu: String              // OPS, FICC, EST, EQD
  ├── team: String            // COB, Collateral, ...
  ├── role: Role              // ADMIN, MANAGER, MEMBER
  └── status: UserStatus      // ACTIVE, INACTIVE

KnowledgeSpace
  ├── spaceId: UUID
  ├── name: String            // "Compliance Q&A", "Collateral Screening"
  ├── description: String
  ├── ownerTeam: String
  ├── language: String        // zh, en
  ├── indexName: String       // kb_compliance_v1
  ├── retrievalConfig: RetrievalConfig  // includes maxAgentRounds, chunkingStrategy, metadataExtractionPrompt
  ├── status: SpaceStatus
  └── accessRules: List<AccessRule>
        ├── targetType: BU | TEAM | USER
        ├── targetValue: String
        └── docSecurityClearance: SecurityLevel
```

### 4.2 Document Context

```java
Document (Aggregate Root)
  ├── documentId: UUID
  ├── spaceId: UUID
  ├── title: String
  ├── fileType: FileType      // PDF, WORD, EXCEL
  ├── securityLevel: SecurityLevel  // ALL, MANAGEMENT
  ├── tags: List<String>
  ├── status: DocumentStatus  // UPLOADED → PARSING → PARSED → INDEXING → INDEXED → FAILED
  ├── currentVersion: DocumentVersion
  ├── versions: List<DocumentVersion>
  ├── chunkCount: Integer
  ├── uploadedBy: UUID
  └── metadata: Map<String, String>

DocumentVersion
  ├── versionId: UUID
  ├── versionNo: Integer
  ├── filePath: String
  ├── fileSize: Long
  ├── checksum: String        // MD5
  ├── createdAt: Instant
  └── createdBy: UUID
```

**Document State Machine:**

```
UPLOADED ──▶ PARSING ──▶ PARSED ──▶ INDEXING ──▶ INDEXED
    │            │                      │
    └────────────┴──────────────────────┴──▶ FAILED (retryable)
```

### 4.3 Knowledge Context

```java
KnowledgeChunk
  ├── chunkId: UUID
  ├── documentId: UUID
  ├── documentVersionId: UUID
  ├── spaceId: UUID
  ├── content: String
  ├── chunkIndex: Integer
  ├── pageNumber: Integer     // for paragraph-level citation
  ├── sectionPath: String     // full parent title tree, e.g. "Ch1 Financial > Art2 Reimbursement > Sec3"
  ├── tokenCount: Integer
  ├── metadata: Map           // inherits securityLevel, tags, etc.
  └── extractedTags: Map      // LLM-extracted structured tags, e.g. {"applicable_client":["institutional"],"product_type":["derivatives"]}
  // embedding vector stored in OpenSearch, not in domain model
```

### 4.4 Conversation Context

```java
ChatSession (Aggregate Root)
  ├── sessionId: UUID
  ├── userId: UUID
  ├── spaceId: UUID
  ├── title: String
  ├── status: SessionStatus   // ACTIVE, ARCHIVED
  ├── messages: List<Message>
  ├── createdAt: Instant
  └── lastActiveAt: Instant

Message
  ├── messageId: UUID
  ├── role: Role              // USER, ASSISTANT, SYSTEM
  ├── content: String
  ├── citations: List<Citation>
  ├── agentTrace: AgentTrace  // optional, for debugging
  ├── tokenCount: Integer
  └── createdAt: Instant

Citation
  ├── citationIndex: Integer  // [1], [2], ...
  ├── documentId: UUID
  ├── documentTitle: String
  ├── chunkId: String
  ├── pageNumber: Integer
  ├── sectionPath: String
  └── snippet: String         // source text excerpt
```

---

## 5. Core Data Flows

### 5.1 File Upload → Knowledge Indexing (Command Side, Event-Driven)

```
User uploads file
  │
  ▼
┌──────────────┐    DocumentUploadedEvent    ┌──────────────────┐
│ Document API │ ──────────────────────────▶ │ ParseEventHandler │
│ (REST)       │                             │                   │
│ ·Validate    │                             │ ·Call DocParserSpi│
│  permissions │                             │ ·Parse to chunks  │
│ ·Store file  │                             │ ·Update doc status│
│ ·Save meta   │                             └────────┬──────────┘
│ ·Publish evt │                                      │
└──────────────┘                          DocumentParsedEvent
                                                     │
                                                     ▼
┌──────────────────┐    ChunksIndexedEvent    ┌──────────────────┐
│  WebSocket push  │ ◀─────────────────────── │ IndexEventHandler │
│  (status update) │                           │                   │
└──────────────────┘                           │ ·LLM metadata     │
                                               │  extraction (tags)│
                                               │ ·Call EmbeddingSpi│
                                               │  generate vectors │
                                               │ ·Write OpenSearch │
                                               │ ·Update doc status│
                                               └──────────────────┘
```

**Data Ingestion Notes:**

- **Chunking Strategy**: Use semantic chunking based on document headers. For compliance docs with strong hierarchy (Chapter > Article > Section), preserve the full parent title tree in `section_path`. Chunking strategy is configured per space via `RetrievalConfig.chunkingStrategy`.
- **Excel Handling**: Docling parses Excel to Markdown tables. For large tables, split by rows (per `<tr>`) rather than by token count to avoid breaking table structure. Alternatively, convert rows to key-value text (e.g., `Client Type: Institutional, Required Docs: Business License...`) before embedding.
- **LLM Metadata Extraction**: During indexing, each chunk is passed through LLM (via `LlmPort`) to extract structured tags (e.g., `applicable_client`, `product_type`, `regulation_scope`). The extraction prompt is configured per space via `RetrievalConfig.metadataExtractionPrompt`. These tags are stored in `extracted_tags` field and used by Agent Planner as filter conditions during retrieval.

### 5.2 Knowledge Q&A (Query Side, Agent ReAct Loop)

```
User asks question
  │
  ▼
┌───────────────┐
│ Chat API (SSE)│
│ ·Load context │
│ ·Check auth   │
└──────┬────────┘
       │
       ▼
┌──────────────────────────────────────────────────┐
│              Agent ReAct Loop                     │
│                                                   │
│  ┌──────────┐    ┌───────────┐    ┌───────────┐  │
│  │  THINK   │───▶│    ACT    │───▶│ EVALUATE  │  │
│  │(Planner) │    │(Executor) │    │(Evaluator)│  │
│  │          │    │           │    │           │  │
│  │·Analyze  │    │·Query     │    │·Results   │  │
│  │ intent   │    │ rewrite   │    │ sufficient│  │
│  │·Plan     │    │·Hybrid    │    │·Need more?│  │
│  │ retrieval│    │ search    │    │·Max rounds?│ │
│  │·Split    │    │·Rerank    │    │           │  │
│  │ sub-query│    │           │    │           │  │
│  └──────────┘    └───────────┘    └─────┬─────┘  │
│       ▲                                 │        │
│       └──── insufficient, retry ────────┘        │
│                                 │                 │
│                           sufficient/max          │
│                                 │                 │
│                                 ▼                 │
│                        ┌──────────────┐           │
│                        │  GENERATE    │           │
│                        │ ·Assemble ctx│           │
│                        │ ·Stream resp │           │
│                        │ ·Attach cite │           │
│                        └──────────────┘           │
└──────────────────────────────────────────────────┘
       │
       ▼ SSE streaming push
┌──────────────┐
│   Frontend   │
│ ·Typewriter  │
│ ·Citation    │
│  side panel  │
└──────────────┘
```

**Agent Single-Round Retrieval Detail:**

```
ACT Phase:
  1. Query Rewrite: LLM rewrites/splits user query
  2. Embedding: Call EmbeddingPort to vectorize query
  3. Hybrid Search: OpenSearch executes simultaneously:
     ├── Vector search (knn)
     └── Keyword search (BM25)
  4. Permission Filter: security_level filter
  5. Rerank: Call RerankPort for precision ranking
  6. Return Top-K chunks + metadata

EVALUATE Phase:
  LLM self-assessment:
  "Based on retrieved results, can the user's question be fully answered?
   If not, what else needs to be searched?"
  → Output: {sufficient: bool, missing_aspects: [...], next_queries: [...]}
```

---

## 6. Agent Orchestration Engine

### 6.1 Core Abstractions

```
┌─────────────────────────────────────────────────────────┐
│                  AgentOrchestrator                        │
│                                                          │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐            │
│  │ Planner  │──▶│ Executor │──▶│Evaluator │──┐         │
│  └──────────┘   └──────────┘   └──────────┘  │         │
│       ▲                                       │         │
│       └──── not enough, re-plan with feedback ┘         │
│                                       │                  │
│                                 sufficient/max           │
│                                       ▼                  │
│                               ┌──────────────┐          │
│                               │  Generator   │          │
│                               │  (streaming) │          │
│                               └──────────────┘          │
└─────────────────────────────────────────────────────────┘
```

### 6.2 Component Interfaces

```java
// 1. Planner — analyze intent, plan retrieval strategy
public interface RetrievalPlanner {
    RetrievalPlan plan(PlanContext context);
}

public record PlanContext(
    String userQuery,
    List<ChatMessage> history,
    RetrievalConfig spaceConfig,
    List<RetrievalFeedback> feedback  // empty on first round
) {}

public record RetrievalPlan(
    List<SubQuery> subQueries,
    SearchStrategy strategy,          // VECTOR, KEYWORD, HYBRID
    int topK
) {}

public record SubQuery(
    String rewrittenQuery,
    String intent
) {}

// 2. Executor — execute retrieval
public interface RetrievalExecutor {
    List<RetrievalResult> execute(RetrievalPlan plan, SearchFilter filter);
}

public record SearchFilter(
    String indexName,
    SecurityLevel userClearance,
    List<String> accessibleTags
) {}

public record RetrievalResult(
    String chunkId,
    String documentId,
    String documentTitle,
    String content,
    int pageNumber,
    String sectionTitle,
    double score,
    Map<String, String> highlights
) {}

// 3. Evaluator — assess result sufficiency
public interface RetrievalEvaluator {
    EvaluationResult evaluate(EvaluationContext context);
}

public record EvaluationContext(
    String originalQuery,
    List<SubQuery> executedQueries,
    List<RetrievalResult> results,
    int currentRound,
    int maxRounds                     // default 3, prevents infinite loop
) {}

public record EvaluationResult(
    boolean sufficient,
    String reasoning,
    List<String> missingAspects,
    List<String> suggestedNextQueries
) {}

// 4. Generator — stream answer generation with citations
public interface AnswerGenerator {
    Flux<StreamEvent> generateStream(GenerationContext context);
}

public record GenerationContext(
    String userQuery,
    List<ChatMessage> history,
    List<RetrievalResult> allResults,
    String spaceLanguage
) {}
```

### 6.3 Orchestrator Main Loop

```java
public class AgentOrchestrator {

    private static final int DEFAULT_MAX_ROUNDS = 3;

    public Flux<StreamEvent> orchestrate(AgentRequest request) {
        return Flux.create(sink -> {
            int maxRounds = request.spaceConfig().maxAgentRounds(DEFAULT_MAX_ROUNDS);
            // Compliance: 3 rounds (fast), Collateral: 5 rounds (deep)
            List<RetrievalResult> allResults = new ArrayList<>();
            List<RetrievalFeedback> feedbacks = new ArrayList<>();

            for (int round = 1; round <= maxRounds; round++) {
                // 1. THINK
                sink.next(StreamEvent.agentThinking(round, "Analyzing..."));
                RetrievalPlan plan = planner.plan(new PlanContext(
                    request.query(), request.history(),
                    request.spaceConfig(), feedbacks));

                // 2. ACT
                sink.next(StreamEvent.agentSearching(round, plan.subQueries()));
                List<RetrievalResult> roundResults =
                    executor.execute(plan, request.filter());
                roundResults = rerankPort.rerank(request.query(), roundResults);
                allResults.addAll(roundResults);

                // 3. EVALUATE
                sink.next(StreamEvent.agentEvaluating(round));
                EvaluationResult eval = evaluator.evaluate(new EvaluationContext(
                    request.query(), plan.subQueries(),
                    allResults, round, maxRounds));

                if (eval.sufficient() || round == maxRounds) break;

                feedbacks.add(new RetrievalFeedback(
                    round, eval.missingAspects(), eval.suggestedNextQueries()));
            }

            // 4. GENERATE
            List<RetrievalResult> deduped = deduplicateAndSort(allResults);
            generator.generateStream(new GenerationContext(
                    request.query(), request.history(),
                    deduped, request.spaceLanguage()))
                .doOnNext(sink::next)
                .doOnComplete(sink::complete)
                .doOnError(sink::error)
                .subscribe();
        });
    }
}
```

### 6.4 SSE Stream Event Types

```java
public sealed interface StreamEvent {
    // Agent process events — frontend shows thinking status
    record AgentThinking(int round, String content) implements StreamEvent {}
    record AgentSearching(int round, List<SubQuery> queries) implements StreamEvent {}
    record AgentEvaluating(int round) implements StreamEvent {}

    // Generation events — frontend streams rendering
    record ContentDelta(String delta) implements StreamEvent {}
    record CitationEmit(Citation citation) implements StreamEvent {}

    // Termination events
    record Done(String messageId, int totalCitations) implements StreamEvent {}
    record Error(String code, String message) implements StreamEvent {}
}
```

### 6.5 Multi-Turn Conversation Context Management

```
1. Load from Redis (hit) or PostgreSQL (miss)
2. Context window: System Prompt + [last 10 rounds] + [current message]
3. History sanitization: strip retrieval chunk snippets from past messages,
   keep only User/Assistant natural language exchanges.
   This prevents context pollution from large retrieved passages.
4. Token budget: total = model context window - reserved for generation
   If history exceeds budget → truncate from earliest messages
5. Write-back: after generation → write to Redis (hot cache) + async write to PostgreSQL
```

### 6.6 Knowledge Space System Prompts

Each knowledge space has a dedicated system prompt template:

- **Compliance Q&A**: Chinese language, strict document-grounded answers, cite with [1][2], classify by customer type / investor classification
- **Collateral Screening**: English language, structured parameter extraction (Party A/B, MTA, IA, margin flow), flag ambiguous or conflicting terms across versions

---

## 7. Project Structure

### 7.1 Maven Multi-Module Layout

```
agentic-rag-claude/
├── pom.xml                              // Parent POM, unified dependency versions
│
├── rag-domain/                          // Domain core (ZERO external dependencies)
│   └── src/main/java/com/rag/domain/
│       ├── identity/
│       │   ├── model/                   // User, KnowledgeSpace, AccessRule
│       │   ├── service/                 // SpaceAuthorizationService
│       │   └── port/                    // UserRepository, AuthPort
│       ├── document/
│       │   ├── model/                   // Document, DocumentVersion, DocumentStatus
│       │   ├── service/                 // DocumentLifecycleService
│       │   ├── event/                   // DocumentUploadedEvent, DocumentParsedEvent
│       │   └── port/                    // DocumentRepository, FileStoragePort
│       ├── knowledge/
│       │   ├── model/                   // KnowledgeChunk, RetrievalResult
│       │   ├── service/                 // KnowledgeIndexService, KnowledgeSearchService
│       │   └── port/                    // VectorStorePort, EmbeddingPort, RerankPort
│       ├── conversation/
│       │   ├── model/                   // ChatSession, Message, Citation, AgentTrace
│       │   ├── service/                 // ChatService, AgentOrchestrator
│       │   └── port/                    // SessionRepository, LlmPort
│       └── shared/
│           ├── model/                   // SecurityLevel, PageResult, etc.
│           └── event/                   // DomainEvent base class
│
├── rag-application/                     // Application services (orchestrate domain, no biz logic)
│   └── src/main/java/com/rag/application/
│       ├── command/                     // Command side handlers
│       ├── query/                       // Query side handlers
│       └── event/                       // Event processing orchestration
│
├── rag-adapter-inbound/                 // Inbound adapters (REST/SSE/WebSocket)
│   └── src/main/java/com/rag/adapter/inbound/
│       ├── rest/                        // Controllers
│       ├── websocket/                   // WebSocket handlers
│       └── dto/                         // Request/Response DTOs
│
├── rag-adapter-outbound/                // Outbound adapters (SPI implementations)
│   └── src/main/java/com/rag/adapter/outbound/
│       ├── llm/                         // AliCloud / Gateway LLM adapters
│       ├── embedding/                   // AliCloud / Gateway embedding adapters
│       ├── rerank/                      // AliCloud / Gateway rerank adapters
│       ├── vectorstore/                 // Local / AWS OpenSearch adapters
│       ├── docparser/                   // Docling / AWS Bedrock adapters
│       ├── storage/                     // Local FS / S3 adapters
│       └── persistence/                 // JPA Repository implementations + Entities
│
├── rag-infrastructure/                  // Infrastructure config
│   └── src/main/java/com/rag/infrastructure/
│       ├── config/
│       │   ├── ServiceRegistryConfig    // ★ Unified service/API config entry
│       │   ├── OpenSearchConfig
│       │   ├── SecurityConfig
│       │   ├── WebSocketConfig
│       │   └── RedisConfig
│       └── spi/
│           └── SpiAutoConfiguration     // Auto-wire SPI implementations by Profile
│
├── rag-boot/                            // Boot module (startup only)
│   ├── src/main/java/com/rag/RagApplication.java
│   └── src/main/resources/
│       ├── application.yml              // Common config
│       ├── application-local.yml        // Local: AliCloud + docling + local OS
│       └── application-aws.yml          // AWS: Gateway + Bedrock + AWS OS
│
├── rag-frontend/                        // React frontend
│   └── (see Section 9)
│
└── docker/
    ├── docker-compose.yml
    └── opensearch/
        └── index-templates/
```

### 7.2 Module Dependency Graph

```
rag-boot
  ├── rag-adapter-inbound
  │     └── rag-application
  │           └── rag-domain        ← Core, depends on NOTHING
  ├── rag-adapter-outbound
  │     └── rag-domain
  └── rag-infrastructure
        └── rag-domain
```

**Critical Constraint**: `rag-domain` pom.xml must NOT import Spring, JPA, OpenSearch, or any framework dependency. Only pure Java + Reactor Core (for `Flux` streaming).

### 7.3 SPI Mechanism

Domain layer defines Port interface → Outbound adapter provides multiple implementations → `SpiAutoConfiguration` injects by Profile.

Example (LLM):

```java
// rag-domain: Port interface (zero dependencies)
public interface LlmPort {
    Flux<String> streamChat(LlmRequest request);
    String chat(LlmRequest request);
}

// rag-adapter-outbound: AliCloud implementation
@Component
@Profile("local")
public class AliCloudLlmAdapter implements LlmPort { ... }

// rag-adapter-outbound: Company gateway implementation
@Component
@Profile("aws")
public class GatewayLlmAdapter implements LlmPort { ... }
```

### 7.4 Unified Configuration Entry

```java
// ServiceRegistryConfig — single entry point for ALL service/API configuration
@Configuration
public class ServiceRegistryConfig {
    @Bean @Profile("local")
    @ConfigurationProperties("rag.services.llm.alicloud")
    public LlmProperties aliCloudLlmProperties() { ... }

    @Bean @Profile("aws")
    @ConfigurationProperties("rag.services.llm.gateway")
    public LlmProperties gatewayLlmProperties() { ... }
    // ... same pattern for embedding, rerank, vector store, doc parser
}
```

```yaml
# application-local.yml
rag:
  services:
    llm:
      alicloud:
        api-key: ${ALICLOUD_LLM_API_KEY}
        model: qwen-plus
        base-url: https://dashscope.aliyuncs.com
    embedding:
      alicloud:
        api-key: ${ALICLOUD_EMB_API_KEY}
        model: text-embedding-v3
    rerank:
      alicloud:
        api-key: ${ALICLOUD_RERANK_API_KEY}
        model: gte-rerank
    vector-store:
      opensearch:
        url: http://localhost:9200
    doc-parser:
      docling:
        url: http://localhost:5001
```

---

## 8. Database Design

### 8.1 PostgreSQL Schema

```sql
-- ============================================================
-- Identity Context
-- ============================================================

CREATE TABLE t_user (
    user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(64)  NOT NULL UNIQUE,
    display_name  VARCHAR(128) NOT NULL,
    email         VARCHAR(256),
    bu            VARCHAR(32)  NOT NULL,
    team          VARCHAR(64)  NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'MEMBER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_bu_team ON t_user(bu, team);

CREATE TABLE t_knowledge_space (
    space_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(128)  NOT NULL,
    description       TEXT,
    owner_team        VARCHAR(64)   NOT NULL,
    language          VARCHAR(8)    NOT NULL DEFAULT 'zh',
    index_name        VARCHAR(128)  NOT NULL UNIQUE,
    retrieval_config  JSONB         NOT NULL DEFAULT '{}',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE t_access_rule (
    rule_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id               UUID        NOT NULL REFERENCES t_knowledge_space(space_id),
    target_type            VARCHAR(16) NOT NULL,
    target_value           VARCHAR(64) NOT NULL,
    doc_security_clearance VARCHAR(16) NOT NULL DEFAULT 'ALL',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_access_rule_space  ON t_access_rule(space_id);
CREATE INDEX idx_access_rule_target ON t_access_rule(target_type, target_value);

CREATE TABLE t_space_permission (
    permission_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES t_user(user_id),
    space_id       UUID        NOT NULL REFERENCES t_knowledge_space(space_id),
    access_level   VARCHAR(16) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, space_id)
);

-- ============================================================
-- Document Context
-- ============================================================

CREATE TABLE t_document (
    document_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id           UUID         NOT NULL REFERENCES t_knowledge_space(space_id),
    title              VARCHAR(512) NOT NULL,
    file_type          VARCHAR(16)  NOT NULL,
    security_level     VARCHAR(16)  NOT NULL DEFAULT 'ALL',
    status             VARCHAR(16)  NOT NULL DEFAULT 'UPLOADED',
    current_version_id UUID,
    chunk_count        INTEGER      NOT NULL DEFAULT 0,
    uploaded_by        UUID         NOT NULL REFERENCES t_user(user_id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_space        ON t_document(space_id);
CREATE INDEX idx_document_space_status ON t_document(space_id, status);

CREATE TABLE t_document_version (
    version_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID          NOT NULL REFERENCES t_document(document_id) ON DELETE CASCADE,
    version_no    INTEGER       NOT NULL,
    file_path     VARCHAR(1024) NOT NULL,
    file_size     BIGINT        NOT NULL,
    checksum      VARCHAR(64)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    UUID          NOT NULL REFERENCES t_user(user_id),
    UNIQUE(document_id, version_no)
);

CREATE TABLE t_document_tag (
    tag_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID        NOT NULL REFERENCES t_document(document_id) ON DELETE CASCADE,
    tag_name     VARCHAR(64) NOT NULL,
    UNIQUE(document_id, tag_name)
);
CREATE INDEX idx_document_tag_name ON t_document_tag(tag_name);

CREATE TABLE t_document_process_log (
    log_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID         NOT NULL REFERENCES t_document(document_id),
    version_id   UUID         NOT NULL REFERENCES t_document_version(version_id),
    action       VARCHAR(32)  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    message      TEXT,
    started_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

-- ============================================================
-- Conversation Context
-- ============================================================

CREATE TABLE t_chat_session (
    session_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES t_user(user_id),
    space_id       UUID         NOT NULL REFERENCES t_knowledge_space(space_id),
    title          VARCHAR(256),
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_active_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_user_space ON t_chat_session(user_id, space_id);
CREATE INDEX idx_session_active     ON t_chat_session(user_id, status, last_active_at DESC);

CREATE TABLE t_message (
    message_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID        NOT NULL REFERENCES t_chat_session(session_id) ON DELETE CASCADE,
    role          VARCHAR(16) NOT NULL,
    content       TEXT        NOT NULL,
    agent_trace   JSONB,
    token_count   INTEGER,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_session ON t_message(session_id, created_at);

CREATE TABLE t_citation (
    citation_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id     UUID         NOT NULL REFERENCES t_message(message_id) ON DELETE CASCADE,
    citation_index INTEGER      NOT NULL,
    document_id    UUID         NOT NULL REFERENCES t_document(document_id),
    chunk_id       VARCHAR(128) NOT NULL,
    document_title VARCHAR(512) NOT NULL,
    page_number    INTEGER,
    section_path   VARCHAR(512),
    snippet        TEXT         NOT NULL
);
CREATE INDEX idx_citation_message ON t_citation(message_id);
```

### 8.2 Redis Cache Design

```
# Hot session cache
session:{sessionId}:messages    → List<Message>         TTL: 2h
session:{sessionId}:meta        → SessionMeta           TTL: 2h

# User permission cache
user:{userId}:spaces            → List<SpacePermission> TTL: 10min

# Document parse status (for WebSocket push)
doc:{documentId}:status         → {status, progress, message}  TTL: 1h

# Chat rate limiting
ratelimit:chat:{userId}         → counter               TTL: 1min
```

### 8.3 OpenSearch Index Design

**Index Naming**: `kb_{space_name}_v{version}`, e.g., `kb_compliance_v1`, `kb_collateral_v1`

**Index Template** (applied to `kb_*`):

```json
{
  "index_patterns": ["kb_*"],
  "template": {
    "settings": {
      "number_of_shards": 2,
      "number_of_replicas": 1,
      "knn": true,
      "knn.algo_param.ef_search": 256,
      "analysis": {
        "analyzer": {
          "ik_smart_analyzer": {
            "type": "custom",
            "tokenizer": "ik_smart",
            "filter": ["lowercase"]
          },
          "ik_max_analyzer": {
            "type": "custom",
            "tokenizer": "ik_max_word",
            "filter": ["lowercase"]
          }
        }
      }
    },
    "mappings": {
      "properties": {
        "chunk_id":            { "type": "keyword" },
        "document_id":         { "type": "keyword" },
        "document_version_id": { "type": "keyword" },
        "space_id":            { "type": "keyword" },
        "content": {
          "type": "text",
          "analyzer": "ik_max_analyzer",
          "search_analyzer": "ik_smart_analyzer",
          "fields": {
            "keyword": { "type": "keyword", "ignore_above": 256 },
            "english": { "type": "text", "analyzer": "english" }
          }
        },
        "embedding": {
          "type": "knn_vector",
          "dimension": 1024,
          "method": {
            "name": "hnsw",
            "space_type": "cosinesimil",
            "engine": "nmslib",
            "parameters": { "ef_construction": 512, "m": 16 }
          }
        },
        "chunk_index":       { "type": "integer" },
        "page_number":       { "type": "integer" },
        "section_path":      { "type": "text", "analyzer": "ik_smart_analyzer",
                               "fields": { "keyword": { "type": "keyword" } } },
        "document_title":    { "type": "text", "analyzer": "ik_smart_analyzer",
                               "fields": { "keyword": { "type": "keyword" } } },
        "extracted_tags":    { "type": "flattened" },
        "security_level":    { "type": "keyword" },
        "tags":              { "type": "keyword" },
        "file_type":         { "type": "keyword" },
        "language":          { "type": "keyword" },
        "uploaded_by":       { "type": "keyword" },
        "upload_time":       { "type": "date" },
        "indexed_at":        { "type": "date" }
      }
    }
  }
}
```

**Key Design Decisions:**

| Decision | Reason |
|----------|--------|
| `content` uses `ik_max_word` indexing + `ik_smart` search | Max-granularity tokenization at index time for recall; smart tokenization at search time for precision |
| `content.english` sub-field | Collateral scenario uses English agreements; English analyzer supports stemming |
| `knn_vector` dimension 1024 | Matches Alibaba Cloud text-embedding-v3 output dimension |
| HNSW ef_construction=512, m=16 | Balanced precision/speed for 150+ document scale |
| `security_level` as filter clause | Filters don't affect scoring and are cacheable — optimal for permission filtering |

**Hybrid Search Query Template:**

```json
{
  "size": 20,
  "_source": { "excludes": ["embedding"] },
  "query": {
    "bool": {
      "must": [{
        "bool": {
          "should": [
            { "script_score": { "query": {"match_all": {}}, "script": {
                "source": "knn_score", "lang": "knn",
                "params": { "field": "embedding", "query_value": "[vector]", "space_type": "cosinesimil" }
            }}},
            { "multi_match": {
                "query": "[user_query]",
                "fields": ["content^3", "content.english^2", "section_title^2", "document_title"],
                "type": "best_fields"
            }}
          ]
        }
      }],
      "filter": [
        { "term":  { "security_level": "[user_clearance]" } },
        { "terms": { "tags": "[accessible_tags]" } }
      ]
    }
  },
  "highlight": {
    "fields": {
      "content": { "fragment_size": 200, "number_of_fragments": 3,
                    "pre_tags": ["<mark>"], "post_tags": ["</mark>"] }
    }
  }
}
```

---

## 9. Frontend Architecture

### 9.1 Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | React 18 + TypeScript |
| Build | Vite |
| UI Components | shadcn/ui |
| State | Zustand |
| Streaming | SSE (chat) + WebSocket (notifications) |

### 9.2 Typography System

| Usage | Font | Weights |
|-------|------|---------|
| Headings / Brand | Bricolage Grotesque | 800, 900 |
| Body Text | IBM Plex Sans | 200, 400 |
| Code / Citations | JetBrains Mono | 400 |

Design principles:
- Weight contrast: 200 thin vs 900 black
- Size ratio: 3:1+ (e.g., 48px hero vs 16px body)
- Self-hosted fonts via woff2 for performance

### 9.3 Design Tokens

```typescript
export const theme = {
  font: {
    display: "var(--font-display)",    // Bricolage Grotesque
    body: "var(--font-body)",          // IBM Plex Sans
    mono: "var(--font-mono)",          // JetBrains Mono
  },
  weight: { thin: 200, regular: 400, bold: 800, black: 900 },
  fontSize: {
    hero: "3rem", h1: "2.25rem", h2: "1.5rem",
    body: "1rem", caption: "0.8125rem", code: "0.875rem",
  },
  color: {
    bg:      { primary: "#0A0A0F", secondary: "#12121A", tertiary: "#1A1A26" },
    text:    { primary: "#E8E6F0", secondary: "#9B97AD", muted: "#5C586E" },
    accent:  { blue: "#6C8EFF", purple: "#A78BFA", green: "#34D399" },
    status:  { parsing: "#F59E0B", indexed: "#34D399", failed: "#EF4444" },
    citation:{ bg: "#1E1B2E", border: "#3B3558", hover: "#2A2640" },
  },
  radius:  { sm: "6px", md: "10px", lg: "16px", pill: "9999px" },
  spacing: { xs: "4px", sm: "8px", md: "16px", lg: "24px", xl: "40px" },
};
```

### 9.4 Page Layouts

**Chat Page (Core)**

```
┌───────────────────────────────────────────────────────────────┐
│  Header                        [Space Selector ▼]   [Avatar] │
├────────┬────────────────────────────────┬─────────────────────┤
│Session │     Chat Area                  │  Citation Panel     │
│ List   │                                │                     │
│        │  [Agent thinking indicator]    │  ┌───────────────┐  │
│[+New]  │                                │  │ doc title     │  │
│        │  ┌────────────────────────┐    │  │ v2.1 | P.12   │  │
│ Sess1  │  │ Assistant:             │    │  │ "excerpt..."  │  │
│ Sess2  │  │ Per AML Manual[1],...  │    │  ├───────────────┤  │
│ Sess3  │  │ KYC Guide[2] requires..│   │  │ doc title     │  │
│        │  └────────────────────────┘    │  │ v1.3 | P.5    │  │
│        │                                │  │ "excerpt..."  │  │
│        │  ┌────────────────────────┐    │  └───────────────┘  │
│        │  │ [Input...         Send]│    │                     │
│        │  └────────────────────────┘    │                     │
└────────┴────────────────────────────────┴─────────────────────┘
```

**Documents Page**

```
┌───────────────────────────────────────────────────────────────┐
│  Header                        [Space Selector ▼]   [Avatar] │
├───────────────────────────────────────────────────────────────┤
│  [Upload] [Batch Tag] [Batch Delete]    Search: [_________]  │
├───────────────────────────────────────────────────────────────┤
│  □  Name           Type  Ver   Level  Status    Tags   Acts  │
│  ─────────────────────────────────────────────────────────── │
│  □  AML Manual     PDF   v2.1  ALL    ● Indexed  comp   ··· │
│  □  KYC Guide      WORD  v1.3  ALL    ● Indexed  comp   ··· │
│  □  ISDA-A.pdf     PDF   v1.0  MGMT   ◐ Parsing  agree  ··· │
│  □  Margin Flow    EXCEL v3.0  ALL    ● Indexed  proc   ··· │
│  □  CSA Template   PDF   v1.0  ALL    ✕ Failed   agree  Rty │
├───────────────────────────────────────────────────────────────┤
│  150 documents | Indexed: 142 | Parsing: 5 | Failed: 3       │
└───────────────────────────────────────────────────────────────┘
```

---

## 10. API Design

### 10.1 Knowledge Space APIs

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/spaces` | Create knowledge space |
| GET | `/api/v1/spaces` | List user's accessible spaces |
| GET | `/api/v1/spaces/{spaceId}` | Get space details |
| PUT | `/api/v1/spaces/{spaceId}` | Update space config |
| PUT | `/api/v1/spaces/{spaceId}/access-rules` | Update access rules |

### 10.2 Document APIs

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/spaces/{spaceId}/documents/upload` | Upload file (multipart) |
| POST | `/api/v1/spaces/{spaceId}/documents/batch-upload` | Batch upload |
| GET | `/api/v1/spaces/{spaceId}/documents` | List documents (paginated/filtered/sorted) |
| GET | `/api/v1/spaces/{spaceId}/documents/{docId}` | Document details |
| DELETE | `/api/v1/spaces/{spaceId}/documents/{docId}` | Delete document |
| POST | `/api/v1/spaces/{spaceId}/documents/{docId}/versions` | Upload new version |
| GET | `/api/v1/spaces/{spaceId}/documents/{docId}/versions` | Version history |
| POST | `/api/v1/spaces/{spaceId}/documents/{docId}/retry` | Retry parsing |
| PUT | `/api/v1/spaces/{spaceId}/documents/batch-tags` | Batch update tags |
| PUT | `/api/v1/spaces/{spaceId}/documents/batch-metadata` | Batch update metadata |
| DELETE | `/api/v1/spaces/{spaceId}/documents/batch-delete` | Batch delete |

### 10.3 Chat APIs

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/spaces/{spaceId}/sessions` | Create session |
| GET | `/api/v1/spaces/{spaceId}/sessions` | List sessions |
| GET | `/api/v1/sessions/{sessionId}` | Session detail (with history) |
| DELETE | `/api/v1/sessions/{sessionId}` | Delete session |
| POST | `/api/v1/sessions/{sessionId}/chat` | Send message (SSE streaming response) |
| POST | `/api/v1/sessions/{sessionId}/stop` | Stop generation |

### 10.4 User APIs

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/users/me` | Current user info & permissions |
| GET | `/api/v1/users` | User list (admin) |
| PUT | `/api/v1/users/{userId}/permissions` | Update user permissions |

### 10.5 SSE Streaming Response Format

```
event: agent_thinking
data: {"step":"query_rewrite","content":"Analyzing your question..."}

event: agent_searching
data: {"step":"retrieval","round":1,"query":"FICC AML account opening materials"}

event: agent_evaluating
data: {"step":"evaluate","round":1,"sufficient":false}

event: agent_searching
data: {"step":"retrieval","round":2,"query":"investor classification due diligence"}

event: content_delta
data: {"delta":"According to"}

event: content_delta
data: {"delta":" the AML Manual"}

event: citation
data: {"index":1,"documentId":"xxx","title":"AML Manual","page":12,"section":"3.2","snippet":"..."}

event: content_delta
data: {"delta":"[1], FICC account opening requires..."}

event: done
data: {"messageId":"xxx","totalCitations":2}
```

### 10.6 WebSocket Notification Format

```
Endpoint: ws://host/ws/notifications?token={jwt}

// Server push message
{
  "type": "DOCUMENT_STATUS_CHANGED",
  "payload": {
    "documentId": "xxx",
    "status": "PARSED",
    "progress": 85,
    "message": "Parsing page 12/15"
  }
}
```

---

## 11. Docker Compose (Local Development)

```yaml
version: "3.9"
services:
  opensearch:
    image: opensearchproject/opensearch:2.17.0
    container_name: rag-opensearch
    environment:
      - discovery.type=single-node
      - DISABLE_SECURITY_PLUGIN=true
      - "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
      - "9600:9600"
    volumes:
      - opensearch-data:/usr/share/opensearch/data

  opensearch-dashboards:
    image: opensearchproject/opensearch-dashboards:2.17.0
    container_name: rag-opensearch-dashboards
    environment:
      - 'OPENSEARCH_HOSTS=["http://opensearch:9200"]'
      - DISABLE_SECURITY_DASHBOARDS_PLUGIN=true
    ports:
      - "5601:5601"
    depends_on:
      - opensearch

  docling:
    image: ds4sd/docling-serve:latest
    container_name: rag-docling
    ports:
      - "5001:5001"
    deploy:
      resources:
        limits:
          memory: 4G

  postgresql:
    image: postgres:16
    container_name: rag-postgresql
    environment:
      POSTGRES_DB: rag_db
      POSTGRES_USER: rag_user
      POSTGRES_PASSWORD: rag_password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    container_name: rag-redis
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru

volumes:
  opensearch-data:
  pgdata:
```

---

## 12. Multi-Agent Evolution Path

### 12.1 Phase 1 (Current) — Single Agent

```
┌─────────────────────┐
│  AgentOrchestrator   │  ← Contains Planner/Executor/Evaluator/Generator
│  (handles all spaces)│
└─────────────────────┘
```

### 12.2 Phase 2 (Future) — Multi-Agent

```
┌──────────────────────────────────────────────┐
│              SupervisorAgent                   │
│  (intent routing — which domain?)              │
└──────┬──────────────┬──────────────┬──────────┘
       ▼              ▼              ▼
┌────────────┐ ┌────────────┐ ┌────────────┐
│ Compliance │ │ Collateral │ │  Future    │
│   Agent    │ │   Agent    │ │  Agent     │
└────────────┘ └────────────┘ └────────────┘
```

### 12.3 Why No Refactoring Needed

| Current | Future | Why |
|---------|--------|-----|
| Single `AgentOrchestrator` | One per domain | Orchestrator is stateless; inject different Planner/Evaluator at construction |
| Space `retrievalConfig` | Per-agent strategy | Config already isolated by space |
| `ChatController` unified entry | `SupervisorAgent` routing | Controller unchanged; add routing in Application layer |
| Spring ApplicationEvent | Inter-agent event communication | Event mechanism already in place; just add new event types |

---

## 13. Technology Stack Summary

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | Spring Boot | 3.4.x |
| AI Framework | Spring AI | 1.0.x |
| Language | Java | 21 (LTS) |
| Build | Maven | Multi-module |
| Database | PostgreSQL | 16 |
| Cache | Redis | 7 |
| Vector Search | OpenSearch | 2.17 |
| Doc Parsing | docling-serve (Docker) | latest |
| LLM | Alibaba Cloud DashScope (qwen-plus) | — |
| Embedding | Alibaba Cloud text-embedding-v3 | 1024 dim |
| Rerank | Alibaba Cloud gte-rerank | — |
| Frontend | React 18 + TypeScript + Vite | — |
| UI Components | shadcn/ui | — |
| State Management | Zustand | — |
| Streaming | SSE (chat) + WebSocket (notifications) | — |
| Container | Docker Compose | Local dev |
| ORM | Spring Data JPA + Hibernate | — |

---

## 14. Non-Functional Requirements

| Dimension | Constraint |
|-----------|-----------|
| Response Latency | Compliance: first token < 3s; Collateral: first token < 10s (deep analysis allowed) |
| Agent Rounds | Configurable per space. Compliance: max 3 rounds; Collateral: max 5 rounds |
| Session Context | Sliding window of 10 rounds, dynamic token budget truncation |
| File Size | Single file ≤ 100MB |
| Concurrency | Compliance 100/day + Collateral 20/day, peak design 10 QPS |
| Audit | All conversations persisted to PostgreSQL for post-hoc audit |
| Hot Cache | Redis TTL 2h for active sessions |
| Permission Cache | Redis TTL 10min, near real-time permission change propagation |
