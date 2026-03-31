# Plan 3: Document Processing Pipeline

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the async document processing pipeline: when a file is uploaded (Plan 2 publishes `DocumentUploadedEvent`), the system asynchronously parses it via docling, chunks it with semantic header splitting, extracts metadata via LLM, generates embeddings, and indexes into OpenSearch. WebSocket notifications push status updates to the frontend.

**Architecture:** Event-driven pipeline with Spring `@Async` + `ApplicationEventListener`. Three stages: Parse → Extract+Embed → Index. Each stage updates document status and publishes the next event. SPI adapters implement docling (DocParserPort), Alibaba Cloud DashScope (EmbeddingPort, LlmPort for metadata extraction), and local OpenSearch (VectorStorePort).

**Tech Stack:** Spring AI (ChatClient, EmbeddingModel), OpenSearch Java Client 2.17, docling-serve REST API, Spring WebSocket (STOMP), Spring Async

**Depends on:** Plan 1 (infrastructure), Plan 2 (Document domain, DocumentUploadedEvent, FileStoragePort)

---

## File Structure

```
rag-domain/src/main/java/com/rag/domain/
├── document/
│   └── event/
│       ├── DocumentUploadedEvent.java     (exists)
│       ├── DocumentParsedEvent.java       (create)
│       └── ChunksIndexedEvent.java        (create)
├── knowledge/
│   ├── model/KnowledgeChunk.java          (create)
│   └── service/KnowledgeIndexService.java (create)

rag-application/src/main/java/com/rag/application/
├── document/
│   └── DocumentApplicationService.java    (exists)
└── event/
    ├── ParseEventHandler.java             (create)
    └── IndexEventHandler.java             (create)

rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/
├── docparser/DoclingJavaAdapter.java      (create)
├── embedding/AliCloudEmbeddingAdapter.java (create)
├── llm/AliCloudLlmAdapter.java           (create)
└── vectorstore/LocalOpenSearchAdapter.java (create)

rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/
└── websocket/
    ├── WebSocketConfig.java               (create)
    └── DocumentStatusNotifier.java        (create)

rag-infrastructure/src/main/java/com/rag/infrastructure/
└── config/
    └── AsyncConfig.java                   (create)

rag-boot/src/main/resources/
└── opensearch/
    └── index-template.json                (create)
```

---

### Task 1: Domain Events + KnowledgeChunk Model

**Files:**
- Create: `rag-domain/src/main/java/com/rag/domain/document/event/DocumentParsedEvent.java`
- Create: `rag-domain/src/main/java/com/rag/domain/document/event/ChunksIndexedEvent.java`
- Create: `rag-domain/src/main/java/com/rag/domain/knowledge/model/KnowledgeChunk.java`

- [ ] **Step 1: Create DocumentParsedEvent**

`rag-domain/src/main/java/com/rag/domain/document/event/DocumentParsedEvent.java`:
```java
package com.rag.domain.document.event;

import com.rag.domain.document.port.DocParserPort;
import com.rag.domain.shared.event.DomainEvent;

import java.util.List;
import java.util.UUID;

public class DocumentParsedEvent extends DomainEvent {
    private final UUID documentId;
    private final UUID versionId;
    private final UUID spaceId;
    private final String documentTitle;
    private final List<DocParserPort.ParsedChunk> chunks;

    public DocumentParsedEvent(UUID documentId, UUID versionId, UUID spaceId,
                                String documentTitle, List<DocParserPort.ParsedChunk> chunks) {
        this.documentId = documentId;
        this.versionId = versionId;
        this.spaceId = spaceId;
        this.documentTitle = documentTitle;
        this.chunks = chunks;
    }

    public UUID getDocumentId() { return documentId; }
    public UUID getVersionId() { return versionId; }
    public UUID getSpaceId() { return spaceId; }
    public String getDocumentTitle() { return documentTitle; }
    public List<DocParserPort.ParsedChunk> getChunks() { return chunks; }
}
```

- [ ] **Step 2: Create ChunksIndexedEvent**

`rag-domain/src/main/java/com/rag/domain/document/event/ChunksIndexedEvent.java`:
```java
package com.rag.domain.document.event;

import com.rag.domain.shared.event.DomainEvent;
import java.util.UUID;

public class ChunksIndexedEvent extends DomainEvent {
    private final UUID documentId;
    private final UUID spaceId;
    private final int chunkCount;

    public ChunksIndexedEvent(UUID documentId, UUID spaceId, int chunkCount) {
        this.documentId = documentId;
        this.spaceId = spaceId;
        this.chunkCount = chunkCount;
    }

    public UUID getDocumentId() { return documentId; }
    public UUID getSpaceId() { return spaceId; }
    public int getChunkCount() { return chunkCount; }
}
```

- [ ] **Step 3: Create KnowledgeChunk domain model**

`rag-domain/src/main/java/com/rag/domain/knowledge/model/KnowledgeChunk.java`:
```java
package com.rag.domain.knowledge.model;

import java.util.Map;
import java.util.UUID;

public record KnowledgeChunk(
    String chunkId,
    UUID documentId,
    UUID documentVersionId,
    UUID spaceId,
    String content,
    int chunkIndex,
    int pageNumber,
    String sectionPath,
    int tokenCount,
    String documentTitle,
    Map<String, Object> extractedTags,
    Map<String, Object> metadata
) {}
```

- [ ] **Step 4: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-domain -q && echo "OK"`
Expected: OK

```bash
git add rag-domain/src/main/java/com/rag/domain/document/event/DocumentParsedEvent.java \
        rag-domain/src/main/java/com/rag/domain/document/event/ChunksIndexedEvent.java \
        rag-domain/src/main/java/com/rag/domain/knowledge/model/KnowledgeChunk.java
git commit -m "feat(domain): add DocumentParsedEvent, ChunksIndexedEvent, and KnowledgeChunk model"
```

---

### Task 2: KnowledgeIndexService (Domain)

**Files:**
- Create: `rag-domain/src/main/java/com/rag/domain/knowledge/service/KnowledgeIndexService.java`

- [ ] **Step 1: Create KnowledgeIndexService**

This service converts parsed chunks + embeddings + extracted tags into `VectorStorePort.ChunkDocument` for indexing.

`rag-domain/src/main/java/com/rag/domain/knowledge/service/KnowledgeIndexService.java`:
```java
package com.rag.domain.knowledge.service;

import com.rag.domain.knowledge.model.KnowledgeChunk;
import com.rag.domain.knowledge.port.VectorStorePort;
import com.rag.domain.shared.model.SecurityLevel;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KnowledgeIndexService {

    public List<VectorStorePort.ChunkDocument> buildChunkDocuments(
            List<KnowledgeChunk> chunks, List<float[]> embeddings) {

        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                "Chunks and embeddings size mismatch: " + chunks.size() + " vs " + embeddings.size());
        }

        return java.util.stream.IntStream.range(0, chunks.size())
            .mapToObj(i -> {
                KnowledgeChunk chunk = chunks.get(i);
                float[] embedding = embeddings.get(i);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("document_id", chunk.documentId().toString());
                metadata.put("document_version_id", chunk.documentVersionId().toString());
                metadata.put("space_id", chunk.spaceId().toString());
                metadata.put("chunk_index", chunk.chunkIndex());
                metadata.put("page_number", chunk.pageNumber());
                metadata.put("section_path", chunk.sectionPath());
                metadata.put("document_title", chunk.documentTitle());
                metadata.put("indexed_at", Instant.now().toString());

                if (chunk.extractedTags() != null && !chunk.extractedTags().isEmpty()) {
                    metadata.put("extracted_tags", chunk.extractedTags());
                }
                if (chunk.metadata() != null) {
                    metadata.putAll(chunk.metadata());
                }

                return new VectorStorePort.ChunkDocument(
                    chunk.chunkId(), chunk.documentId().toString(),
                    chunk.content(), embedding, metadata
                );
            }).toList();
    }
}
```

- [ ] **Step 2: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-domain -q && echo "OK"`
Expected: OK

```bash
git add rag-domain/src/main/java/com/rag/domain/knowledge/service/
git commit -m "feat(domain): add KnowledgeIndexService for building chunk documents with embeddings"
```

---

### Task 3: AsyncConfig

**Files:**
- Create: `rag-infrastructure/src/main/java/com/rag/infrastructure/config/AsyncConfig.java`

- [ ] **Step 1: Create async executor config**

`rag-infrastructure/src/main/java/com/rag/infrastructure/config/AsyncConfig.java`:
```java
package com.rag.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "documentProcessingExecutor")
    public Executor documentProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("doc-process-");
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add rag-infrastructure/src/main/java/com/rag/infrastructure/config/AsyncConfig.java
git commit -m "feat(infra): add async thread pool config for document processing"
```

---

### Task 4: Docling Adapter (DocParserPort implementation)

**Files:**
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/docparser/DoclingJavaAdapter.java`

- [ ] **Step 1: Create docling REST client adapter**

Docling-serve exposes a REST API at `POST /v1/convert` that accepts file upload and returns structured document.

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/docparser/DoclingJavaAdapter.java`:
```java
package com.rag.adapter.outbound.docparser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.domain.document.port.DocParserPort;
import com.rag.infrastructure.config.ServiceRegistryConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local")
public class DoclingJavaAdapter implements DocParserPort {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DoclingJavaAdapter(ServiceRegistryConfig.DocParserProperties props,
                               ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
            .baseUrl(props.getUrl())
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
            .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public ParseResult parse(String fileName, InputStream content) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("files", new InputStreamResource(content))
            .filename(fileName)
            .contentType(MediaType.APPLICATION_OCTET_STREAM);

        String response = webClient.post()
            .uri("/v1/convert")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return parseDoclingResponse(response);
    }

    private ParseResult parseDoclingResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode document = root.has("document") ? root.get("document") : root;

            List<ParsedChunk> chunks = new ArrayList<>();
            int totalPages = 0;

            // Docling returns structured content with pages and text elements
            if (document.has("pages")) {
                totalPages = document.get("pages").size();
            }

            // Extract text content from main_text or body
            JsonNode mainText = document.has("main_text") ? document.get("main_text") :
                                document.has("body") ? document.get("body") : null;

            if (mainText != null && mainText.isArray()) {
                StringBuilder currentChunk = new StringBuilder();
                String currentSection = "";
                int currentPage = 1;
                int chunkIndex = 0;

                for (JsonNode element : mainText) {
                    String text = element.has("text") ? element.get("text").asText() : "";
                    String type = element.has("type") ? element.get("type").asText() : "paragraph";
                    int page = element.has("prov") && element.get("prov").isArray()
                        && element.get("prov").size() > 0
                        && element.get("prov").get(0).has("page")
                        ? element.get("prov").get(0).get("page").asInt() : currentPage;

                    if (text.isEmpty()) continue;

                    // Section headers start new chunks (semantic chunking)
                    if (type.contains("header") || type.contains("title")) {
                        if (!currentChunk.isEmpty()) {
                            chunks.add(new ParsedChunk(
                                currentChunk.toString().trim(), currentPage,
                                currentSection, estimateTokens(currentChunk.toString())));
                            chunkIndex++;
                            currentChunk = new StringBuilder();
                        }
                        currentSection = currentSection.isEmpty() ? text :
                            currentSection + " > " + text;
                        currentPage = page;
                    }

                    currentChunk.append(text).append("\n");

                    // Also split on size: if chunk exceeds ~1500 tokens, cut
                    if (estimateTokens(currentChunk.toString()) > 1500) {
                        chunks.add(new ParsedChunk(
                            currentChunk.toString().trim(), currentPage,
                            currentSection, estimateTokens(currentChunk.toString())));
                        chunkIndex++;
                        currentChunk = new StringBuilder();
                    }
                }

                // Flush remaining
                if (!currentChunk.isEmpty()) {
                    chunks.add(new ParsedChunk(
                        currentChunk.toString().trim(), currentPage,
                        currentSection, estimateTokens(currentChunk.toString())));
                }
            }

            // Fallback: if no structured content, use raw text with simple splitting
            if (chunks.isEmpty() && document.has("text")) {
                String fullText = document.get("text").asText();
                chunks.addAll(splitBySize(fullText, 1500));
                totalPages = 1;
            }

            return new ParseResult(chunks, totalPages);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse docling response", e);
        }
    }

    private List<ParsedChunk> splitBySize(String text, int maxTokens) {
        List<ParsedChunk> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            if (estimateTokens(current.toString() + para) > maxTokens && !current.isEmpty()) {
                chunks.add(new ParsedChunk(current.toString().trim(), 1, "", estimateTokens(current.toString())));
                current = new StringBuilder();
            }
            current.append(para).append("\n\n");
        }
        if (!current.isEmpty()) {
            chunks.add(new ParsedChunk(current.toString().trim(), 1, "", estimateTokens(current.toString())));
        }
        return chunks;
    }

    private int estimateTokens(String text) {
        // Rough estimate: 1 token ≈ 4 chars for English, ~2 chars for Chinese
        return text.length() / 3;
    }
}
```

- [ ] **Step 2: Add WebClient dependency to rag-adapter-outbound POM**

Add to `rag-adapter-outbound/pom.xml` inside `<dependencies>`:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
```

- [ ] **Step 3: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-adapter-outbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/docparser/ \
        rag-adapter-outbound/pom.xml
git commit -m "feat(adapter): add DoclingJavaAdapter — parses documents via docling-serve REST API with semantic chunking"
```

---

### Task 5: AliCloud Embedding Adapter

**Files:**
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/embedding/AliCloudEmbeddingAdapter.java`

- [ ] **Step 1: Create embedding adapter using Spring AI**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/embedding/AliCloudEmbeddingAdapter.java`:
```java
package com.rag.adapter.outbound.embedding;

import com.rag.domain.knowledge.port.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("local")
public class AliCloudEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;

    public AliCloudEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.call(
            new org.springframework.ai.embedding.EmbeddingRequest(
                List.of(text), null));
        return response.getResults().get(0).getOutput();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse response = embeddingModel.call(
            new org.springframework.ai.embedding.EmbeddingRequest(texts, null));
        return response.getResults().stream()
            .map(r -> r.getOutput())
            .toList();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/embedding/
git commit -m "feat(adapter): add AliCloudEmbeddingAdapter using Spring AI EmbeddingModel"
```

---

### Task 6: AliCloud LLM Adapter

**Files:**
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/llm/AliCloudLlmAdapter.java`

- [ ] **Step 1: Create LLM adapter using Spring AI ChatClient**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/llm/AliCloudLlmAdapter.java`:
```java
package com.rag.adapter.outbound.llm;

import com.rag.domain.conversation.port.LlmPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local")
public class AliCloudLlmAdapter implements LlmPort {

    private final ChatClient chatClient;

    public AliCloudLlmAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Flux<String> streamChat(LlmRequest request) {
        return chatClient.prompt()
            .system(request.systemPrompt())
            .messages(toSpringMessages(request.history()))
            .user(request.userMessage())
            .stream()
            .content();
    }

    @Override
    public String chat(LlmRequest request) {
        return chatClient.prompt()
            .system(request.systemPrompt())
            .messages(toSpringMessages(request.history()))
            .user(request.userMessage())
            .call()
            .content();
    }

    private List<Message> toSpringMessages(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) return List.of();
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            switch (msg.role()) {
                case "user" -> messages.add(new UserMessage(msg.content()));
                case "assistant" -> messages.add(new AssistantMessage(msg.content()));
                case "system" -> messages.add(new SystemMessage(msg.content()));
            }
        }
        return messages;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/llm/
git commit -m "feat(adapter): add AliCloudLlmAdapter using Spring AI ChatClient with streaming support"
```

---

### Task 7: Local OpenSearch Adapter (VectorStorePort)

**Files:**
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/vectorstore/LocalOpenSearchAdapter.java`
- Create: `rag-boot/src/main/resources/opensearch/index-template.json`

- [ ] **Step 1: Create OpenSearch index template file**

`rag-boot/src/main/resources/opensearch/index-template.json`:
```json
{
  "index_patterns": ["kb_*"],
  "template": {
    "settings": {
      "number_of_shards": 2,
      "number_of_replicas": 0,
      "knn": true,
      "knn.algo_param.ef_search": 256
    },
    "mappings": {
      "properties": {
        "chunk_id":            { "type": "keyword" },
        "document_id":         { "type": "keyword" },
        "document_version_id": { "type": "keyword" },
        "space_id":            { "type": "keyword" },
        "content":             { "type": "text" },
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
        "section_path":      { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
        "document_title":    { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
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

- [ ] **Step 2: Create LocalOpenSearchAdapter**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/vectorstore/LocalOpenSearchAdapter.java`:
```java
package com.rag.adapter.outbound.vectorstore;

import com.rag.domain.knowledge.port.VectorStorePort;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Profile("local")
public class LocalOpenSearchAdapter implements VectorStorePort {

    private final OpenSearchClient client;

    public LocalOpenSearchAdapter(OpenSearchClient client) {
        this.client = client;
    }

    @Override
    public void upsertChunks(String indexName, List<ChunkDocument> chunks) {
        try {
            ensureIndexExists(indexName);

            List<BulkOperation> operations = chunks.stream().map(chunk -> {
                Map<String, Object> doc = new HashMap<>();
                doc.put("chunk_id", chunk.chunkId());
                doc.put("document_id", chunk.documentId());
                doc.put("content", chunk.content());
                doc.put("embedding", chunk.embedding());
                if (chunk.metadata() != null) {
                    doc.putAll(chunk.metadata());
                }
                return BulkOperation.of(b -> b.index(
                    IndexOperation.of(idx -> idx.index(indexName).id(chunk.chunkId()).document(doc))
                ));
            }).toList();

            BulkResponse response = client.bulk(BulkRequest.of(b -> b.operations(operations)));
            if (response.errors()) {
                throw new RuntimeException("Bulk index errors: " +
                    response.items().stream()
                        .filter(i -> i.error() != null)
                        .map(i -> i.error().reason())
                        .collect(Collectors.joining("; ")));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to upsert chunks to " + indexName, e);
        }
    }

    @Override
    public void deleteByDocumentId(String indexName, String documentId) {
        try {
            client.deleteByQuery(d -> d
                .index(indexName)
                .query(q -> q.term(t -> t.field("document_id").value(documentId)))
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete by document_id: " + documentId, e);
        }
    }

    @Override
    public List<SearchHit> hybridSearch(String indexName, HybridSearchRequest request) {
        try {
            // Build filter queries
            List<Query> filterQueries = new ArrayList<>();
            if (request.filters() != null) {
                request.filters().forEach((key, value) -> {
                    if (value instanceof List<?> list) {
                        filterQueries.add(Query.of(q -> q.terms(t -> t
                            .field(key)
                            .terms(tv -> tv.value(list.stream()
                                .map(v -> org.opensearch.client.opensearch._types.FieldValue.of(v.toString()))
                                .toList()))
                        )));
                    } else {
                        filterQueries.add(Query.of(q -> q.term(t -> t
                            .field(key).value(value.toString())
                        )));
                    }
                });
            }

            // BM25 text search
            Query textQuery = Query.of(q -> q.multiMatch(m -> m
                .query(request.query())
                .fields("content^3", "section_path^2", "document_title")
            ));

            // KNN vector search
            Query knnQuery = Query.of(q -> q.knn(k -> k
                .field("embedding")
                .vector(toFloatList(request.queryVector()))
                .k(request.topK())
            ));

            // Combine: should (text OR knn), filter
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
                .should(textQuery, knnQuery)
                .minimumShouldMatch("1");
            if (!filterQueries.isEmpty()) {
                boolBuilder.filter(filterQueries);
            }

            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(indexName)
                .size(request.topK())
                .query(q -> q.bool(boolBuilder.build()))
                .source(src -> src.filter(f -> f.excludes("embedding")))
                .highlight(h -> h
                    .fields("content", hf -> hf
                        .fragmentSize(200)
                        .numberOfFragments(3)
                        .preTags("<mark>")
                        .postTags("</mark>")
                    )
                )
            );

            SearchResponse<Map> response = client.search(searchRequest, Map.class);

            return response.hits().hits().stream().map(hit -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = hit.source();
                Map<String, List<String>> highlights = hit.highlight() != null
                    ? hit.highlight() : Map.of();

                return new SearchHit(
                    (String) source.getOrDefault("chunk_id", hit.id()),
                    (String) source.getOrDefault("document_id", ""),
                    (String) source.getOrDefault("content", ""),
                    hit.score() != null ? hit.score() : 0.0,
                    source,
                    highlights
                );
            }).toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to search " + indexName, e);
        }
    }

    private void ensureIndexExists(String indexName) throws IOException {
        boolean exists = client.indices().exists(
            ExistsRequest.of(e -> e.index(indexName))).value();
        if (!exists) {
            client.indices().create(CreateIndexRequest.of(c -> c
                .index(indexName)
                .settings(s -> s
                    .knn(true)
                    .numberOfShards("2")
                    .numberOfReplicas("0")
                )
                .mappings(m -> m
                    .properties("chunk_id", p -> p.keyword(k -> k))
                    .properties("document_id", p -> p.keyword(k -> k))
                    .properties("content", p -> p.text(t -> t))
                    .properties("embedding", p -> p.knnVector(knn -> knn
                        .dimension(1024)
                    ))
                    .properties("section_path", p -> p.text(t -> t))
                    .properties("document_title", p -> p.text(t -> t))
                    .properties("extracted_tags", p -> p.flattened(f -> f))
                    .properties("security_level", p -> p.keyword(k -> k))
                    .properties("tags", p -> p.keyword(k -> k))
                    .properties("page_number", p -> p.integer(i -> i))
                    .properties("chunk_index", p -> p.integer(i -> i))
                )
            ));
        }
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }
}
```

- [ ] **Step 3: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-adapter-outbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/vectorstore/ \
        rag-boot/src/main/resources/opensearch/
git commit -m "feat(adapter): add LocalOpenSearchAdapter with hybrid search, auto index creation, and bulk upsert"
```

---

### Task 8: Event Handlers (Parse + Index Pipeline)

**Files:**
- Create: `rag-application/src/main/java/com/rag/application/event/ParseEventHandler.java`
- Create: `rag-application/src/main/java/com/rag/application/event/IndexEventHandler.java`

- [ ] **Step 1: Create ParseEventHandler**

Listens to `DocumentUploadedEvent`, calls DocParserPort, publishes `DocumentParsedEvent`.

`rag-application/src/main/java/com/rag/application/event/ParseEventHandler.java`:
```java
package com.rag.application.event;

import com.rag.domain.document.event.DocumentParsedEvent;
import com.rag.domain.document.event.DocumentUploadedEvent;
import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentStatus;
import com.rag.domain.document.port.DocParserPort;
import com.rag.domain.document.port.DocumentRepository;
import com.rag.domain.document.port.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class ParseEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ParseEventHandler.class);

    private final DocParserPort docParserPort;
    private final FileStoragePort fileStoragePort;
    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ParseEventHandler(DocParserPort docParserPort,
                              FileStoragePort fileStoragePort,
                              DocumentRepository documentRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.docParserPort = docParserPort;
        this.fileStoragePort = fileStoragePort;
        this.documentRepository = documentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Async("documentProcessingExecutor")
    @EventListener
    public void handle(DocumentUploadedEvent event) {
        log.info("Parsing document: {} ({})", event.getDocumentId(), event.getFileName());

        Document document = documentRepository.findById(event.getDocumentId()).orElse(null);
        if (document == null) {
            log.error("Document not found: {}", event.getDocumentId());
            return;
        }

        try {
            // Transition: UPLOADED → PARSING
            document.transitionTo(DocumentStatus.PARSING);
            documentRepository.save(document);

            // Parse via docling
            InputStream fileStream = fileStoragePort.retrieve(event.getFilePath());
            DocParserPort.ParseResult result = docParserPort.parse(event.getFileName(), fileStream);

            // Transition: PARSING → PARSED
            document.transitionTo(DocumentStatus.PARSED);
            document.setChunkCount(result.chunks().size());
            documentRepository.save(document);

            log.info("Parsed {} chunks from document {}", result.chunks().size(), event.getDocumentId());

            // Publish next event
            eventPublisher.publishEvent(new DocumentParsedEvent(
                event.getDocumentId(), event.getVersionId(), event.getSpaceId(),
                document.getTitle(), result.chunks()
            ));
        } catch (Exception e) {
            log.error("Failed to parse document: {}", event.getDocumentId(), e);
            document.transitionTo(DocumentStatus.FAILED);
            documentRepository.save(document);
        }
    }
}
```

- [ ] **Step 2: Create IndexEventHandler**

Listens to `DocumentParsedEvent`, extracts metadata via LLM, generates embeddings, indexes to OpenSearch, publishes `ChunksIndexedEvent`.

`rag-application/src/main/java/com/rag/application/event/IndexEventHandler.java`:
```java
package com.rag.application.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.domain.conversation.port.LlmPort;
import com.rag.domain.document.event.ChunksIndexedEvent;
import com.rag.domain.document.event.DocumentParsedEvent;
import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentStatus;
import com.rag.domain.document.port.DocParserPort;
import com.rag.domain.document.port.DocumentRepository;
import com.rag.domain.identity.model.KnowledgeSpace;
import com.rag.domain.identity.port.SpaceRepository;
import com.rag.domain.knowledge.model.KnowledgeChunk;
import com.rag.domain.knowledge.port.EmbeddingPort;
import com.rag.domain.knowledge.port.VectorStorePort;
import com.rag.domain.knowledge.service.KnowledgeIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class IndexEventHandler {

    private static final Logger log = LoggerFactory.getLogger(IndexEventHandler.class);

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final LlmPort llmPort;
    private final DocumentRepository documentRepository;
    private final SpaceRepository spaceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final KnowledgeIndexService knowledgeIndexService;

    public IndexEventHandler(EmbeddingPort embeddingPort, VectorStorePort vectorStorePort,
                              LlmPort llmPort, DocumentRepository documentRepository,
                              SpaceRepository spaceRepository,
                              ApplicationEventPublisher eventPublisher,
                              ObjectMapper objectMapper) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.llmPort = llmPort;
        this.documentRepository = documentRepository;
        this.spaceRepository = spaceRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.knowledgeIndexService = new KnowledgeIndexService();
    }

    @Async("documentProcessingExecutor")
    @EventListener
    public void handle(DocumentParsedEvent event) {
        log.info("Indexing {} chunks for document {}", event.getChunks().size(), event.getDocumentId());

        Document document = documentRepository.findById(event.getDocumentId()).orElse(null);
        if (document == null) return;

        KnowledgeSpace space = spaceRepository.findById(event.getSpaceId()).orElse(null);
        if (space == null) return;

        try {
            // Transition: PARSED → INDEXING
            document.transitionTo(DocumentStatus.INDEXING);
            documentRepository.save(document);

            // Delete old chunks for this document (in case of re-index)
            vectorStorePort.deleteByDocumentId(space.getIndexName(), event.getDocumentId().toString());

            // 1. Build KnowledgeChunk list
            List<KnowledgeChunk> knowledgeChunks = new ArrayList<>();
            List<String> textsToEmbed = new ArrayList<>();

            String metadataPrompt = space.getRetrievalConfig() != null
                ? space.getRetrievalConfig().metadataExtractionPrompt() : "";

            for (int i = 0; i < event.getChunks().size(); i++) {
                DocParserPort.ParsedChunk chunk = event.getChunks().get(i);

                // LLM metadata extraction (if prompt configured)
                Map<String, Object> extractedTags = Map.of();
                if (metadataPrompt != null && !metadataPrompt.isEmpty()) {
                    extractedTags = extractMetadata(metadataPrompt, chunk.content());
                }

                // Document-level metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("security_level", document.getSecurityLevel().name());
                metadata.put("tags", document.getTags());
                metadata.put("file_type", document.getFileType().name());
                metadata.put("language", space.getLanguage());
                metadata.put("uploaded_by", document.getUploadedBy().toString());

                knowledgeChunks.add(new KnowledgeChunk(
                    UUID.randomUUID().toString(),
                    event.getDocumentId(), event.getVersionId(), event.getSpaceId(),
                    chunk.content(), i, chunk.pageNumber(), chunk.sectionPath(),
                    chunk.tokenCount(), event.getDocumentTitle(),
                    extractedTags, metadata
                ));
                textsToEmbed.add(chunk.content());
            }

            // 2. Batch embedding
            log.info("Generating embeddings for {} chunks", textsToEmbed.size());
            List<float[]> embeddings = embeddingPort.embedBatch(textsToEmbed);

            // 3. Build chunk documents and upsert
            var chunkDocs = knowledgeIndexService.buildChunkDocuments(knowledgeChunks, embeddings);
            vectorStorePort.upsertChunks(space.getIndexName(), chunkDocs);

            // Transition: INDEXING → INDEXED
            document.transitionTo(DocumentStatus.INDEXED);
            document.setChunkCount(knowledgeChunks.size());
            documentRepository.save(document);

            log.info("Successfully indexed {} chunks for document {}", knowledgeChunks.size(), event.getDocumentId());

            eventPublisher.publishEvent(new ChunksIndexedEvent(
                event.getDocumentId(), event.getSpaceId(), knowledgeChunks.size()));

        } catch (Exception e) {
            log.error("Failed to index document: {}", event.getDocumentId(), e);
            document.transitionTo(DocumentStatus.FAILED);
            documentRepository.save(document);
        }
    }

    private Map<String, Object> extractMetadata(String prompt, String chunkContent) {
        try {
            String systemPrompt = prompt + "\n\nReturn ONLY a JSON object with extracted tags. No explanation.";
            String response = llmPort.chat(new LlmPort.LlmRequest(
                systemPrompt, List.of(), chunkContent, 0.1));

            // Parse JSON from LLM response
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json?\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to extract metadata from chunk, skipping: {}", e.getMessage());
            return Map.of();
        }
    }
}
```

- [ ] **Step 3: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-application -q && echo "OK"`
Expected: OK

```bash
git add rag-application/src/main/java/com/rag/application/event/
git commit -m "feat(pipeline): add ParseEventHandler and IndexEventHandler — async event-driven document processing pipeline"
```

---

### Task 9: WebSocket Notifications

**Files:**
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/websocket/WebSocketConfig.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/websocket/DocumentStatusNotifier.java`

- [ ] **Step 1: Create WebSocket STOMP config**

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/websocket/WebSocketConfig.java`:
```java
package com.rag.adapter.inbound.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
```

- [ ] **Step 2: Create DocumentStatusNotifier**

Listens to `ChunksIndexedEvent` and document status changes, pushes to WebSocket subscribers.

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/websocket/DocumentStatusNotifier.java`:
```java
package com.rag.adapter.inbound.websocket;

import com.rag.domain.document.event.ChunksIndexedEvent;
import com.rag.domain.document.event.DocumentParsedEvent;
import com.rag.domain.document.event.DocumentUploadedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DocumentStatusNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public DocumentStatusNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        notify(event.getDocumentId().toString(), "UPLOADED", 0, "File uploaded, queued for parsing");
    }

    @EventListener
    public void onDocumentParsed(DocumentParsedEvent event) {
        notify(event.getDocumentId().toString(), "PARSED", 50,
            "Parsed " + event.getChunks().size() + " chunks, starting indexing");
    }

    @EventListener
    public void onChunksIndexed(ChunksIndexedEvent event) {
        notify(event.getDocumentId().toString(), "INDEXED", 100,
            "Successfully indexed " + event.getChunkCount() + " chunks");
    }

    private void notify(String documentId, String status, int progress, String message) {
        messagingTemplate.convertAndSend("/topic/documents/" + documentId, Map.of(
            "type", "DOCUMENT_STATUS_CHANGED",
            "payload", Map.of(
                "documentId", documentId,
                "status", status,
                "progress", progress,
                "message", message
            )
        ));
    }
}
```

- [ ] **Step 3: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-adapter-inbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/websocket/
git commit -m "feat(websocket): add STOMP WebSocket config and DocumentStatusNotifier for real-time status updates"
```

---

### Task 10: Spring AI Configuration

**Files:**
- Modify: `rag-boot/src/main/resources/application-local.yml`

- [ ] **Step 1: Add Spring AI OpenAI-compatible config for DashScope**

Add to `rag-boot/src/main/resources/application-local.yml`:

```yaml
  ai:
    openai:
      api-key: ${ALICLOUD_LLM_API_KEY:sk-placeholder}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      chat:
        options:
          model: qwen-plus
          temperature: 0.7
      embedding:
        options:
          model: text-embedding-v3
```

This goes under the `spring:` key in the YAML. Spring AI's OpenAI starter auto-configures `ChatClient.Builder` and `EmbeddingModel` beans that our adapters inject.

- [ ] **Step 2: Commit**

```bash
git add rag-boot/src/main/resources/application-local.yml
git commit -m "feat(config): add Spring AI OpenAI-compatible config for DashScope (qwen-plus + text-embedding-v3)"
```

---

### Task 11: Full Build & Pipeline Smoke Test

- [ ] **Step 1: Full Maven build**

Run: `cd E:/AIProject/agentic-rag-claude && mvn clean install -DskipTests`
Expected: BUILD SUCCESS for all modules

- [ ] **Step 2: Start Docker infrastructure (including OpenSearch + docling)**

Run: `cd E:/AIProject/agentic-rag-claude/docker && docker compose up -d`
Wait for all services to be healthy:
Run: `docker compose ps`
Expected: All 5 containers running

- [ ] **Step 3: Start application**

Run: `cd E:/AIProject/agentic-rag-claude && mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=local`
Expected: Application starts. Check logs for:
- `doc-process-` thread pool initialized
- WebSocket `/ws/notifications` endpoint registered

- [ ] **Step 4: Upload a test document and verify pipeline**

Seed test data (if not already done):
```bash
docker exec rag-postgresql psql -U rag_user -d rag_db -c "
INSERT INTO t_user (user_id, username, display_name, bu, team, role, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'testuser', 'Test User', 'OPS', 'COB', 'ADMIN', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;"
```

Create a space:
```bash
curl -s -X POST http://localhost:8080/api/v1/spaces \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Space","description":"test","ownerTeam":"COB","language":"zh","indexName":"kb_test_v1"}'
```

Upload a test PDF:
```bash
# Use any small PDF. If none available, create a text file:
echo "This is a test document about AML compliance requirements for institutional clients." > /tmp/test.pdf
SPACE_ID="<space-id-from-above>"
curl -s -X POST "http://localhost:8080/api/v1/spaces/${SPACE_ID}/documents/upload" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -F "file=@/tmp/test.pdf"
```

Check logs for:
```
Parsing document: <docId> (test.pdf)
Parsed X chunks from document <docId>
Generating embeddings for X chunks
Successfully indexed X chunks for document <docId>
```

Verify document status changed to INDEXED:
```bash
curl -s "http://localhost:8080/api/v1/spaces/${SPACE_ID}/documents" | python -m json.tool
```
Expected: Document with `"status": "INDEXED"`

Verify chunks in OpenSearch:
```bash
curl -s "http://localhost:9200/kb_test_v1/_count" | python -m json.tool
```
Expected: `"count": X` (number of chunks)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: verify Plan 3 complete — document processing pipeline working end-to-end"
```

---

## Plan 3 Summary

After completing all 11 tasks, you will have:
- **Domain Events**: `DocumentParsedEvent`, `ChunksIndexedEvent` completing the event chain
- **KnowledgeChunk Model**: Domain representation of indexed chunks with extractedTags
- **KnowledgeIndexService**: Builds chunk documents with embeddings and metadata
- **Async Pipeline**: `DocumentUploadedEvent` → ParseEventHandler → `DocumentParsedEvent` → IndexEventHandler → `ChunksIndexedEvent`
- **SPI Adapters**:
  - `DoclingJavaAdapter` — semantic chunking via docling-serve REST API
  - `AliCloudEmbeddingAdapter` — batch embedding via Spring AI
  - `AliCloudLlmAdapter` — streaming + sync LLM calls via Spring AI ChatClient
  - `LocalOpenSearchAdapter` — hybrid search, bulk upsert, auto index creation
- **WebSocket**: STOMP over SockJS for real-time document status notifications
- **Spring AI Config**: OpenAI-compatible DashScope integration
- Ready for Plan 4 (Conversation & Agent Engine)
