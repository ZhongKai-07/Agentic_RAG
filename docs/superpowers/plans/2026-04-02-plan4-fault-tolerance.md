# Fault Tolerance Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add HTTP-level LLM timeout, embedding failure fallback to BM25-only search, and a refined `AgentOrchestrator` exception boundary that guarantees SSE always terminates with `done` or `error`.

**Architecture:** Add `timeoutSeconds` to `ServiceRegistryConfig.LlmProperties` and configure it on the `RestClient` bean in `AgentConfig` (HTTP ReadTimeout, not `CompletableFuture`). Add embedding fallback in `HybridRetrievalExecutor`. Refine exception boundary in `AgentOrchestrator` to distinguish `KnowledgeBaseEmptyException`, timeout, and generic errors. Add `onErrorResume` to Generator's Flux.

**Tech Stack:** Java 21, Spring AI `ChatClient`, Spring `RestClient`, JUnit 5, Mockito, Maven (`rag-infrastructure`, `rag-application`, `rag-domain`)

**Spec:** `docs/superpowers/specs/2026-04-02-agent-loop-retrieval-enhancement-design.md` — Section 4

**Execution order note:** Independent of Plans 1, 2, 3. Can be executed in any order.

---

## Chunk 1: LLM HTTP Timeout

### Task 1: Add timeoutSeconds to LlmProperties and configure RestClient

**Files:**
- Modify: `rag-infrastructure/src/main/java/com/rag/infrastructure/config/ServiceRegistryConfig.java`
- Modify: `rag-infrastructure/src/main/java/com/rag/infrastructure/config/AgentConfig.java`
- Modify: `rag-boot/src/main/resources/application.yml` (add default config)

- [ ] **Step 1: Add timeoutSeconds field to LlmProperties**

In `ServiceRegistryConfig.java`, inside the `LlmProperties` class add:

```java
// Add after existing fields (apiKey, model, baseUrl)
private int timeoutSeconds = 30;
public int getTimeoutSeconds() { return timeoutSeconds; }
public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
```

- [ ] **Step 2: Add timeout-seconds default to application.yml**

`application-local.yml` is gitignored (contains API keys) — do not add config there. Add the default to `rag-boot/src/main/resources/application.yml` under the `rag.services.llm` section (create the section if absent):

```yaml
rag:
  services:
    llm:
      timeout-seconds: 30   # HTTP ReadTimeout for synchronous LLM calls
```

- [ ] **Step 3: Configure RestClient with ReadTimeout in AgentConfig**

Replace `AgentConfig.java` fully:

```java
package com.rag.infrastructure.config;

import com.rag.domain.conversation.agent.*;
import com.rag.domain.knowledge.port.RerankPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class AgentConfig {

    @Bean
    public AgentOrchestrator agentOrchestrator(
            RetrievalPlanner planner,
            RetrievalExecutor executor,
            RetrievalEvaluator evaluator,
            AnswerGenerator generator,
            RerankPort rerankPort) {
        return new AgentOrchestrator(planner, executor, evaluator, generator, rerankPort);
    }

    /**
     * Applies HTTP ReadTimeout to the RestClient used by Spring AI's ChatClient.
     * This ensures LLM calls (Planner, Evaluator) timeout at the HTTP connection level,
     * freeing the blocked thread — unlike CompletableFuture.orTimeout() which only
     * cancels the caller.
     */
    /**
     * Spring AI 1.0.0 uses the Spring Boot auto-configured RestClient.Builder
     * (via OpenAiAutoConfiguration), which DOES apply RestClientCustomizer beans.
     * This customizer therefore affects the ChatClient used by LLM calls.
     *
     * Spring 6.x: SimpleClientHttpRequestFactory.setReadTimeout accepts Duration (not int).
     */
    @Bean
    public RestClientCustomizer llmReadTimeoutCustomizer(
            ServiceRegistryConfig.LlmProperties llmProperties) {
        return restClientBuilder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofSeconds(llmProperties.getTimeoutSeconds()));
            restClientBuilder.requestFactory(factory);
        };
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
mvn compile -pl rag-infrastructure -am -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add rag-infrastructure/src/main/java/com/rag/infrastructure/config/ServiceRegistryConfig.java \
        rag-infrastructure/src/main/java/com/rag/infrastructure/config/AgentConfig.java \
        rag-boot/src/main/resources/application.yml
git commit -m "feat: configure LLM HTTP ReadTimeout via RestClientCustomizer — prevents thread pool exhaustion on LLM timeout"
```

---

## Chunk 2: Embedding Fallback

### Task 2: BM25-only fallback when embedding service fails

**Files:**
- Modify: `rag-application/src/main/java/com/rag/application/agent/HybridRetrievalExecutor.java`
- Create: `rag-application/src/test/java/com/rag/application/agent/HybridRetrievalExecutorTest.java`

- [ ] **Step 1: Write failing test**

Create `rag-application/src/test/java/com/rag/application/agent/HybridRetrievalExecutorTest.java`:

```java
package com.rag.application.agent;

import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.knowledge.port.EmbeddingPort;
import com.rag.domain.knowledge.port.VectorStorePort;
import com.rag.domain.shared.model.SecurityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalExecutorTest {

    @Mock EmbeddingPort embeddingPort;
    @Mock VectorStorePort vectorStorePort;
    HybridRetrievalExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new HybridRetrievalExecutor(embeddingPort, vectorStorePort);
    }

    @Test
    void execute_fallsBackToBm25_whenEmbeddingFails() throws Exception {
        when(embeddingPort.embed(anyString())).thenThrow(new RuntimeException("Embedding service down"));
        when(vectorStorePort.hybridSearch(anyString(), any())).thenReturn(List.of(
            new VectorStorePort.SearchHit("chunk-1", "doc-1", "BM25 result", 0.02, Map.of(), Map.of())
        ));

        RetrievalPlan plan = new RetrievalPlan(
            List.of(new SubQuery("test query", "search")),
            RetrievalPlan.SearchStrategy.HYBRID, 10
        );
        SearchFilter filter = new SearchFilter("space-1", null, List.of());

        List<RetrievalResult> results = executor.execute(plan, filter);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo("chunk-1");

        // Verify null vector was passed (BM25-only fallback)
        ArgumentCaptor<VectorStorePort.HybridSearchRequest> captor =
            ArgumentCaptor.forClass(VectorStorePort.HybridSearchRequest.class);
        verify(vectorStorePort).hybridSearch(eq("space-1"), captor.capture());
        assertThat(captor.getValue().queryVector()).isNull();
    }

    @Test
    void execute_continuesOtherSubQueries_whenOneSubQueryFails() throws Exception {
        when(embeddingPort.embed("failing query")).thenThrow(new RuntimeException("partial failure"));
        when(embeddingPort.embed("working query")).thenReturn(new float[]{0.1f, 0.2f});
        when(vectorStorePort.hybridSearch(anyString(), any())).thenReturn(List.of(
            new VectorStorePort.SearchHit("chunk-ok", "doc-1", "result", 0.02, Map.of(), Map.of())
        ));

        RetrievalPlan plan = new RetrievalPlan(
            List.of(
                new SubQuery("failing query", "first"),
                new SubQuery("working query", "second")
            ),
            RetrievalPlan.SearchStrategy.HYBRID, 10
        );
        SearchFilter filter = new SearchFilter("space-1", null, List.of());

        List<RetrievalResult> results = executor.execute(plan, filter);

        // Both sub-queries attempted; failing one falls back to BM25, working one uses vector
        assertThat(results).isNotEmpty();
        verify(vectorStorePort, times(2)).hybridSearch(anyString(), any());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -pl rag-application -Dtest=HybridRetrievalExecutorTest -q
```
Expected: FAIL — `HybridRetrievalExecutor` currently lets embed() exception propagate without fallback.

- [ ] **Step 3: Add embedding fallback to HybridRetrievalExecutor**

In `HybridRetrievalExecutor.java`, replace the sub-query loop's try-catch block:

```java
for (SubQuery subQuery : plan.subQueries()) {
    try {
        // 1. Embed the query — fall back to null (BM25-only) if embedding service fails
        float[] queryVector;
        try {
            queryVector = embeddingPort.embed(subQuery.rewrittenQuery());
        } catch (Exception embedEx) {
            log.warn("Embedding failed for sub-query '{}', falling back to BM25-only: {}",
                subQuery.rewrittenQuery(), embedEx.getMessage());
            queryVector = null;  // LocalOpenSearchAdapter skips KNN when null
        }

        // 2. Build filters
        Map<String, Object> searchFilters = new HashMap<>();
        if (filter.userClearance() != null) {
            List<String> allowedLevels = filter.userClearance() == SecurityLevel.MANAGEMENT
                ? List.of(SecurityLevel.ALL.name(), SecurityLevel.MANAGEMENT.name())
                : List.of(SecurityLevel.ALL.name());
            searchFilters.put("security_level", allowedLevels);
        }

        // 3. Execute hybrid search (queryVector may be null → BM25-only)
        var searchRequest = new VectorStorePort.HybridSearchRequest(
            subQuery.rewrittenQuery(), queryVector,
            searchFilters, plan.topK()
        );
        List<VectorStorePort.SearchHit> hits =
            vectorStorePort.hybridSearch(filter.indexName(), searchRequest);

        // 4. Convert to RetrievalResult (first-occurrence dedup handled by Orchestrator)
        for (var hit : hits) {
            String chunkId = hit.chunkId();
            if (!mergedResults.containsKey(chunkId)) {
                Map<String, String> highlightMap = new HashMap<>();
                if (hit.highlights() != null) {
                    hit.highlights().forEach((k, v) ->
                        highlightMap.put(k, String.join("...", v)));
                }
                mergedResults.put(chunkId, new RetrievalResult(
                    chunkId,
                    hit.documentId(),
                    getMetaString(hit.metadata(), "document_title"),
                    hit.content(),
                    getMetaInt(hit.metadata(), "page_number"),
                    getMetaString(hit.metadata(), "section_path"),
                    hit.score(),
                    highlightMap
                ));
            }
        }
    } catch (Exception e) {
        log.error("Retrieval failed for sub-query '{}': {}",
            subQuery.rewrittenQuery(), e.getMessage());
        if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
            throw new KnowledgeBaseEmptyException(filter.indexName());
        }
        // Other errors: skip this sub-query, continue with the rest
    }
}
```

- [ ] **Step 4: Run tests**

```bash
mvn test -pl rag-application -Dtest=HybridRetrievalExecutorTest -q
```
Expected: Both tests PASS.

- [ ] **Step 5: Commit**

```bash
git add rag-application/src/main/java/com/rag/application/agent/HybridRetrievalExecutor.java \
        rag-application/src/test/java/com/rag/application/agent/HybridRetrievalExecutorTest.java
git commit -m "feat: add embedding fallback to BM25-only in HybridRetrievalExecutor"
```

---

## Chunk 3: Orchestrator Exception Boundary + Generator Error Handling

### Task 3: Refine exception boundary and guarantee SSE termination

**Files:**
- Modify: `rag-domain/src/main/java/com/rag/domain/conversation/agent/AgentOrchestrator.java`
- Modify: `rag-application/src/main/java/com/rag/application/agent/LlmAnswerGenerator.java`
- Create: `rag-domain/src/test/java/com/rag/domain/conversation/agent/AgentOrchestratorExceptionTest.java`

- [ ] **Step 1: Write failing tests**

Create `rag-domain/src/test/java/com/rag/domain/conversation/agent/AgentOrchestratorExceptionTest.java`:

```java
package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.conversation.model.StreamEvent;
import com.rag.domain.identity.model.RetrievalConfig;
import com.rag.domain.knowledge.exception.KnowledgeBaseEmptyException;
import com.rag.domain.knowledge.port.RerankPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorExceptionTest {

    @Mock RetrievalPlanner planner;
    @Mock RetrievalExecutor executor;
    @Mock RetrievalEvaluator evaluator;
    @Mock AnswerGenerator generator;
    @Mock RerankPort rerankPort;

    AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator(planner, executor, evaluator, generator, rerankPort);
    }

    @Test
    void knowledgeBaseEmpty_withNoAccumulatedResults_emitsErrorEvent() {
        when(planner.plan(any())).thenReturn(simplePlan());
        when(executor.execute(any(), any())).thenThrow(new KnowledgeBaseEmptyException("space-1"));

        // Collect all events — avoids brittle positional assertions on SSE event sequence
        List<StreamEvent> events = orchestrator.orchestrate(agentRequest())
            .collectList().block();

        assertThat(events).isNotNull();
        // Last event must be an Error with KNOWLEDGE_BASE_EMPTY code
        StreamEvent last = events.get(events.size() - 1);
        assertThat(last).isInstanceOf(StreamEvent.Error.class);
        assertThat(((StreamEvent.Error) last).code()).isEqualTo("KNOWLEDGE_BASE_EMPTY");
    }

    @Test
    void knowledgeBaseEmpty_withAccumulatedResults_proceedsToGenerate() {
        RetrievalResult existingResult = new RetrievalResult(
            "chunk-1", "doc-1", "Title", "content", 1, "sec", 0.02, Map.of());

        when(planner.plan(any())).thenReturn(simplePlan());
        // Round 1: returns result, evaluator says insufficient
        // Round 2: throws KnowledgeBaseEmptyException
        when(executor.execute(any(), any()))
            .thenReturn(List.of(existingResult))
            .thenThrow(new KnowledgeBaseEmptyException("space-1"));
        when(evaluator.evaluate(any()))
            .thenReturn(new EvaluationResult(false, "not enough", List.of("X"), List.of("retry")));
        when(rerankPort.rerank(any(), any(), anyInt()))
            .thenReturn(List.of(new RerankPort.RerankResult(0, 0.9)));
        when(generator.generateStream(any())).thenReturn(Flux.empty());

        AgentRequest request = new AgentRequest("query", List.of(),
            new RetrievalConfig(2, "semantic_header", "", 3, false, 5, 0.02),
            new SearchFilter("space-1", null, List.of()), "zh");

        orchestrator.orchestrate(request).blockLast();

        // Generator was called with the accumulated result
        verify(generator, times(1)).generateStream(any());
    }

    @Test
    void generalException_emitsErrorEventAndCompletes() {
        when(planner.plan(any())).thenThrow(new RuntimeException("unexpected"));

        List<StreamEvent> events = orchestrator.orchestrate(agentRequest())
            .collectList().block();

        assertThat(events).isNotNull();
        StreamEvent last = events.get(events.size() - 1);
        assertThat(last).isInstanceOf(StreamEvent.Error.class);
        assertThat(((StreamEvent.Error) last).code()).isEqualTo("AGENT_ERROR");
    }

    // --- helpers ---

    private AgentRequest agentRequest() {
        return new AgentRequest("test query", List.of(),
            new RetrievalConfig(), new SearchFilter("space-1", null, List.of()), "zh");
    }

    private RetrievalPlan simplePlan() {
        return new RetrievalPlan(
            List.of(new SubQuery("test query", "search")),
            RetrievalPlan.SearchStrategy.HYBRID, 10
        );
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -pl rag-domain -Dtest=AgentOrchestratorExceptionTest -q
```
Expected: FAIL — current catch block doesn't distinguish exception types.

- [ ] **Step 3: Refine AgentOrchestrator exception boundary**

In `AgentOrchestrator.java`, replace the outer `catch` block (inside `Flux.create`):

```java
} catch (com.rag.domain.knowledge.exception.KnowledgeBaseEmptyException e) {
    if (!mergedResults.isEmpty()) {
        // Partial results available from earlier rounds — proceed to generate
        try {
            List<RetrievalResult> partial = new ArrayList<>(mergedResults.values());
            if (lastPlan != null) {
                String canonicalQuery = lastPlan.subQueries().get(0).rewrittenQuery();
                partial = applyRerank(canonicalQuery, partial);
            }
            GenerationContext genCtx = new GenerationContext(
                request.query(), request.history(), partial, request.spaceLanguage());
            generator.generateStream(genCtx)
                .doOnNext(sink::next)
                .doOnComplete(sink::complete)
                .doOnError(err -> {
                    sink.next(StreamEvent.error("GENERATOR_ERROR", err.getMessage()));
                    sink.complete();
                })
                .subscribe();
        } catch (Exception genEx) {
            sink.next(StreamEvent.error("AGENT_ERROR", genEx.getMessage()));
            sink.complete();
        }
    } else {
        sink.next(StreamEvent.error("KNOWLEDGE_BASE_EMPTY", e.getMessage()));
        sink.complete();
    }
} catch (Exception e) {
    try {
        sink.next(StreamEvent.error("AGENT_ERROR", e.getMessage()));
    } catch (Exception ignored) {
    }
    sink.complete();
}
```

- [ ] **Step 4: Add onErrorResume to LlmAnswerGenerator**

In `LlmAnswerGenerator.java`, update `generateStream()` to wrap the stream with `onErrorResume`:

```java
@Override
public Flux<StreamEvent> generateStream(GenerationContext context) {
    String systemPrompt = buildGenerationPrompt(context);
    String userMessage = buildUserMessage(context);

    AtomicReference<StringBuilder> fullContent = new AtomicReference<>(new StringBuilder());
    UUID messageId = UUID.randomUUID();

    return llmPort.streamChat(new LlmPort.LlmRequest(
                systemPrompt, context.history(), userMessage, 0.7))
        .map(delta -> {
            fullContent.get().append(delta);
            return (StreamEvent) StreamEvent.contentDelta(delta);
        })
        .concatWith(Flux.defer(() -> {
            List<Citation> citations = extractCitations(
                fullContent.get().toString(), context.allResults());
            List<StreamEvent> events = new ArrayList<>();
            for (Citation c : citations) {
                events.add(StreamEvent.citationEmit(c));
            }
            events.add(StreamEvent.done(messageId.toString(), citations.size()));
            return Flux.fromIterable(events);
        }))
        .onErrorResume(e -> {
            // Guarantee SSE always terminates — never leaves frontend hanging
            return Flux.just(StreamEvent.error("GENERATOR_ERROR", e.getMessage()));
        });
}
```

- [ ] **Step 5: Run exception boundary tests**

```bash
mvn test -pl rag-domain -Dtest=AgentOrchestratorExceptionTest -q
```
Expected: All 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add rag-domain/src/main/java/com/rag/domain/conversation/agent/AgentOrchestrator.java \
        rag-application/src/main/java/com/rag/application/agent/LlmAnswerGenerator.java \
        rag-domain/src/test/java/com/rag/domain/conversation/agent/AgentOrchestratorExceptionTest.java
git commit -m "feat: refine AgentOrchestrator exception boundary and add onErrorResume to Generator — SSE always terminates"
```

---

## Verification

- [ ] **Run full test suite across all changed modules**

```bash
mvn test -pl rag-domain,rag-application,rag-infrastructure -q
```
Expected: BUILD SUCCESS, no test failures.

- [ ] **Verify full project compiles**

```bash
mvn compile -pl rag-boot -am -q
```
Expected: BUILD SUCCESS.
