# Bounded Contexts — 领域边界与聚合

## 四大限界上下文

```mermaid
graph TB
    subgraph Identity["Identity 上下文 — 身份与权限"]
        User["User<br/>userId · username · bu · team · role"]
        Space["KnowledgeSpace ⭐<br/>(聚合根)<br/>spaceId · name · language · indexName<br/>RetrievalConfig (JSONB)"]
        Rule["AccessRule<br/>targetType: BU|TEAM|USER<br/>docSecurityClearance: ALL|MANAGEMENT"]
        SpaceAuth["SpaceAuthorizationService<br/>canAccessSpace() · resolveSecurityClearance()"]

        Space -->|1:N| Rule
        SpaceAuth --> User
        SpaceAuth --> Space
    end

    subgraph Document["Document 上下文 — 文档管理"]
        Doc["Document ⭐<br/>(聚合根)<br/>documentId · spaceId · title · tags<br/>status · securityLevel"]
        Version["DocumentVersion<br/>versionNo · filePath · fileSize · checksum"]
        Status["DocumentStatus<br/>状态机"]

        Doc -->|1:N| Version
        Doc --> Status
    end

    subgraph Knowledge["Knowledge 上下文 — 知识索引"]
        Chunk["KnowledgeChunk<br/>chunkId · documentId · spaceId<br/>content · chunkIndex · pageNumber<br/>sectionPath · tokenCount"]
        IndexSvc["KnowledgeIndexService<br/>buildChunkDocument()"]

        IndexSvc --> Chunk
    end

    subgraph Conversation["Conversation 上下文 — 对话引擎"]
        Session["ChatSession ⭐<br/>(聚合根)<br/>sessionId · userId · spaceId<br/>messages · maxRounds=10"]
        Msg["Message<br/>role · content · citations<br/>agentTrace · tokenCount"]
        Cite["Citation<br/>documentId · title · chunkId<br/>pageNumber · sectionPath · snippet"]
        SE["StreamEvent (sealed)<br/>AgentThinking | AgentSearching<br/>AgentEvaluating | ContentDelta<br/>CitationEmit | Done | Error"]
        Orch["AgentOrchestrator<br/>ReAct 循环编排"]

        Session -->|1:N| Msg
        Msg -->|0:N| Cite
        Orch --> SE
    end

    %% 跨上下文通信（领域事件）
    Doc -.->|"DocumentUploadedEvent<br/>(async)"| Knowledge
    Doc -.->|"DocumentParsedEvent<br/>(async)"| Knowledge
    Knowledge -.->|"ChunksIndexedEvent<br/>(async)"| Doc

    Space -.->|"indexName<br/>retrievalConfig"| Conversation
    User -.->|"SecurityLevel<br/>clearance"| Conversation
    Chunk -.->|"检索结果"| Conversation

    style Identity fill:#1b4332,stroke:#52b788,color:#fff
    style Document fill:#3c1642,stroke:#c77dff,color:#fff
    style Knowledge fill:#0a2463,stroke:#3e92cc,color:#fff
    style Conversation fill:#6a040f,stroke:#e85d04,color:#fff
```

## 聚合根与端口

| 上下文 | 聚合根 | 包路径 | 端口 (Port) |
|:---|:---|:---|:---|
| **Identity** | `User`, `KnowledgeSpace` | `com.rag.domain.identity` | `UserRepository`, `SpaceRepository` |
| **Document** | `Document` (含 versions) | `com.rag.domain.document` | `DocumentRepository`, `DocParserPort`, `FileStoragePort` |
| **Knowledge** | `KnowledgeChunk` | `com.rag.domain.knowledge` | `VectorStorePort`, `EmbeddingPort`, `RerankPort` |
| **Conversation** | `ChatSession` (含 messages) | `com.rag.domain.conversation` | `SessionRepository`, `LlmPort` |

## 跨上下文通信 — 领域事件流

```
Document 上下文                    Knowledge 上下文                 前端
    │                                  │                            │
    │  DocumentUploadedEvent           │                            │
    ├─────────────────────────────────►│                            │
    │              (ParseEventHandler) │                            │
    │                                  │ 解析文件                    │
    │                                  │ 语义分块                    │
    │                                  │                            │
    │  DocumentParsedEvent             │                            │
    ├─────────────────────────────────►│                            │
    │              (IndexEventHandler) │                            │
    │                                  │ 批量嵌入                    │
    │                                  │ 写入 OpenSearch             │
    │                                  │                            │
    │  ChunksIndexedEvent              │                            │
    │◄─────────────────────────────────┤                            │
    │                                  │                            │
    │  WebSocket 通知                                               │
    ├──────────────────────────────────────────────────────────────►│
    │  (DocumentStatusNotifier)                                     │
```

## 状态机 — Document Lifecycle

```mermaid
stateDiagram-v2
    [*] --> UPLOADED : 用户上传文件
    UPLOADED --> PARSING : ParseEventHandler 接收事件
    PARSING --> PARSED : Docling 解析完成
    PARSING --> FAILED : 解析异常
    PARSED --> INDEXING : IndexEventHandler 接收事件
    INDEXING --> INDEXED : OpenSearch 写入完成
    INDEXING --> FAILED : 嵌入/索引异常
    FAILED --> PARSING : retryParse() 重试
```

## 关键值对象

### RetrievalConfig (JSONB, 存储在 KnowledgeSpace)
```json
{
  "maxAgentRounds": 3,
  "chunkingStrategy": "SEMANTIC",
  "metadataExtractionPrompt": "Extract key entities..."
}
```

### StreamEvent (sealed interface, 7 种类型)
```
AgentThinking(round, content)      → SSE event: agent_thinking
AgentSearching(round, queries)     → SSE event: agent_searching
AgentEvaluating(round, sufficient) → SSE event: agent_evaluating
ContentDelta(delta)                → SSE event: content_delta
CitationEmit(citation)             → SSE event: citation
Done(messageId, totalCitations)    → SSE event: done
Error(code, message)               → SSE event: error
```
