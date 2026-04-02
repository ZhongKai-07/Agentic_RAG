# Unified Rerank Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-round Rerank with a single unified Rerank call after all retrieval rounds complete, using canonical rewritten query and first-occurrence deduplication.

**Architecture:** Add a null-guard to `LocalOpenSearchAdapter.executeKnnSearch()` to enable BM25-only fallback, then refactor `AgentOrchestrator` to accumulate results with `putIfAbsent`, store the last `RetrievalPlan` reference, and apply one Rerank call post-loop using the canonical rewritten query from the last plan.

**Tech Stack:** Java 21, JUnit 5, Mockito, Maven multi-module (`rag-adapter-outbound`, `rag-domain`)

**Spec:** `docs/superpowers/specs/2026-04-02-agent-loop-retrieval-enhancement-design.md` — Section 2

**Execution order note:** Independent of Plan 1, 3, 4 — can be executed in any order.

---

## Chunk 1: LocalOpenSearchAdapter Null-Guard

### Task 1: Add null-guard for queryVector in executeKnnSearch()

**Files:**
- Modify: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/vectorstore/LocalOpenSearchAdapter.java`
- Create: `rag-adapter-outbound/src/test/java/com/rag/adapter/outbound/vectorstore/LocalOpenSearchAdapterNullVectorTest.java`

- [ ] **Step 1: Write failing test using reflection to invoke executeKnnSearch directly**

Create `rag-adapter-outbound/src/test/java/com/rag/adapter/outbound/vectorstore/LocalOpenSearchAdapterNullVectorTest.java`:

```java
package com.rag.adapter.outbound.vectorstore;

import com.rag.domain.knowledge.port.VectorStorePort;
import com.rag.infrastructure.config.ServiceRegistryConfig;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalOpenSearchAdapterNullVectorTest {

    @Test
    void executeKnnSearch_withNullVector_throwsNpeBeforeNullGuard() throws Exception {
        // This test is RED before the null-guard is added.
        // After adding the null-guard it should return an empty list (GREEN).
        ServiceRegistryConfig.EmbeddingProperties props = new ServiceRegistryConfig.EmbeddingProperties();
        props.setDimension(1024);

        // We cannot call hybridSearch without a real OpenSearch instance.
        // Use reflection to invoke the private executeKnnSearch method directly.
        LocalOpenSearchAdapter adapter = new LocalOpenSearchAdapter(mock(OpenSearchClient.class), props);

        VectorStorePort.HybridSearchRequest request = new VectorStorePort.HybridSearchRequest(
            "test query", null, Map.of(), 10);

        Method method = LocalOpenSearchAdapter.class.getDeclaredMethod(
            "executeKnnSearch", String.class, VectorStorePort.HybridSearchRequest.class, List.class);
        method.setAccessible(true);

        // BEFORE null-guard: NullPointerException when accessing request.queryVector() inside knn builder
        // AFTER null-guard: returns List.of() cleanly
        Object result = method.invoke(adapter, "test-index", request, List.of());
        assertThat((List<?>) result).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails (NPE before null-guard)**

```bash
mvn test -pl rag-adapter-outbound -Dtest=LocalOpenSearchAdapterNullVectorTest -q
```
Expected: FAIL — `InvocationTargetException` wrapping `NullPointerException` when `request.queryVector()` is passed to the KNN builder.

- [ ] **Step 3: Add null-guard to executeKnnSearch()**

In `LocalOpenSearchAdapter.java`, modify `executeKnnSearch()` — add the null-guard as the very first statement:

```java
private List<SearchHit> executeKnnSearch(String indexName, HybridSearchRequest request,
                                          List<Query> filterQueries) throws IOException {
    // Null vector means embedding service was unavailable — skip KNN, BM25 runs alone
    if (request.queryVector() == null) {
        return List.of();
    }
    // ... rest of existing method unchanged
```

- [ ] **Step 4: Verify compilation**

```bash
mvn compile -pl rag-adapter-outbound -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/vectorstore/LocalOpenSearchAdapter.java \
        rag-adapter-outbound/src/test/java/com/rag/adapter/outbound/vectorstore/LocalOpenSearchAdapterNullVectorTest.java
git commit -m "feat: add null-guard to executeKnnSearch to enable BM25-only fallback when embedding unavailable"
```

---

## Chunk 2: AgentOrchestrator Unified Rerank

### Task 2: Refactor AgentOrchestrator accumulation and rerank logic

**Files:**
- Modify: `rag-domain/src/main/java/com/rag/domain/conversation/agent/AgentOrchestrator.java`
- Create: `rag-domain/src/test/java/com/rag/domain/conversation/agent/AgentOrchestratorRerankTest.java`

- [ ] **Step 1: Write failing tests**

Create `rag-domain/src/test/java/com/rag/domain/conversation/agent/AgentOrchestratorRerankTest.java`:

```java
package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.conversation.model.StreamEvent;
import com.rag.domain.identity.model.RetrievalConfig;
import com.rag.domain.knowledge.port.RerankPort;
import com.rag.domain.shared.model.SecurityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorRerankTest {

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
    void rerank_isCalledOnce_afterAllRounds() {
        setupSingleRoundSufficientFlow();

        orchestrator.orchestrate(agentRequest()).blockLast();

        verify(rerankPort, times(1)).rerank(anyString(), anyList(), anyInt());
    }

    @Test
    void rerank_usesCanonicalRewrittenQuery_notRawUserQuery() {
        String rawQuery = "its price";
        String rewrittenQuery = "Product X price";

        RetrievalPlan plan = new RetrievalPlan(
            List.of(new SubQuery(rewrittenQuery, "price lookup")),
            RetrievalPlan.SearchStrategy.HYBRID, 10
        );
        when(planner.plan(any())).thenReturn(plan);
        when(executor.execute(any(), any())).thenReturn(List.of(mockResult("chunk-1")));
        when(evaluator.evaluate(any())).thenReturn(new EvaluationResult(true, "ok", List.of(), List.of()));
        when(rerankPort.rerank(any(), any(), anyInt())).thenReturn(List.of(new RerankPort.RerankResult(0, 0.9)));
        when(generator.generateStream(any())).thenReturn(Flux.empty());

        AgentRequest request = new AgentRequest(rawQuery, List.of(),
            new RetrievalConfig(), new SearchFilter("space-1", null, List.of()), "zh");
        orchestrator.orchestrate(request).blockLast();

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(rerankPort).rerank(queryCaptor.capture(), any(), anyInt());
        assertThat(queryCaptor.getValue()).isEqualTo(rewrittenQuery);
        assertThat(queryCaptor.getValue()).isNotEqualTo(rawQuery);
    }

    @Test
    void dedup_keepFirstOccurrence_whenSameChunkAppearsInMultipleRounds() {
        RetrievalResult round1Chunk = mockResultWithContent("chunk-dup", "round 1 content");
        RetrievalResult round2Chunk = mockResultWithContent("chunk-dup", "round 2 content");

        when(planner.plan(any())).thenReturn(simplePlan());
        when(executor.execute(any(), any()))
            .thenReturn(List.of(round1Chunk))
            .thenReturn(List.of(round2Chunk));
        when(evaluator.evaluate(any()))
            .thenReturn(new EvaluationResult(false, "not enough", List.of("X"), List.of("retry")))
            .thenReturn(new EvaluationResult(true, "ok", List.of(), List.of()));

        ArgumentCaptor<List<String>> contentsCaptor = ArgumentCaptor.forClass(List.class);
        when(rerankPort.rerank(any(), contentsCaptor.capture(), anyInt()))
            .thenReturn(List.of(new RerankPort.RerankResult(0, 0.9)));
        when(generator.generateStream(any())).thenReturn(Flux.empty());

        AgentRequest request = new AgentRequest("query", List.of(),
            new RetrievalConfig(2, "semantic_header", "", 3, false, 5, 0.02),
            new SearchFilter("space-1", null, List.of()), "zh");
        orchestrator.orchestrate(request).blockLast();

        // Exactly 1 unique chunk sent to rerank (dedup by putIfAbsent)
        assertThat(contentsCaptor.getValue()).hasSize(1);
        // First occurrence wins — round 1 content is retained, not round 2
        assertThat(contentsCaptor.getValue().get(0)).isEqualTo("round 1 content");
    }

    // --- helpers ---

    private void setupSingleRoundSufficientFlow() {
        when(planner.plan(any())).thenReturn(simplePlan());
        when(executor.execute(any(), any())).thenReturn(List.of(mockResult("chunk-1")));
        when(evaluator.evaluate(any())).thenReturn(new EvaluationResult(true, "ok", List.of(), List.of()));
        when(rerankPort.rerank(any(), any(), anyInt())).thenReturn(List.of(new RerankPort.RerankResult(0, 0.9)));
        when(generator.generateStream(any())).thenReturn(Flux.empty());
    }

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

    private RetrievalResult mockResult(String chunkId) {
        return new RetrievalResult(chunkId, "doc-1", "Title", "content", 1, "section", 0.02, Map.of());
    }

    private RetrievalResult mockResultWithContent(String chunkId, String content) {
        return new RetrievalResult(chunkId, "doc-1", "Title", content, 1, "section", 0.02, Map.of());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -pl rag-domain -Dtest=AgentOrchestratorRerankTest -q
```
Expected: FAIL — current orchestrator calls rerank per-round and uses raw query.

- [ ] **Step 3: Rewrite AgentOrchestrator**

Replace the full contents of `rag-domain/src/main/java/com/rag/domain/conversation/agent/AgentOrchestrator.java`:

```java
package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.conversation.model.StreamEvent;
import com.rag.domain.knowledge.port.RerankPort;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AgentOrchestrator {

    private static final int DEFAULT_MAX_ROUNDS = 3;

    private final RetrievalPlanner planner;
    private final RetrievalExecutor executor;
    private final RetrievalEvaluator evaluator;
    private final AnswerGenerator generator;
    private final RerankPort rerankPort;

    public AgentOrchestrator(RetrievalPlanner planner,
                              RetrievalExecutor executor,
                              RetrievalEvaluator evaluator,
                              AnswerGenerator generator,
                              RerankPort rerankPort) {
        this.planner = planner;
        this.executor = executor;
        this.evaluator = evaluator;
        this.generator = generator;
        this.rerankPort = rerankPort;
    }

    public Flux<StreamEvent> orchestrate(AgentRequest request) {
        Objects.requireNonNull(request, "AgentRequest must not be null");
        Objects.requireNonNull(request.query(), "Query must not be null");
        Objects.requireNonNull(request.filter(), "SearchFilter must not be null");
        if (request.query().isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }

        return Flux.create(sink -> {
            try {
                int maxRounds = request.spaceConfig().maxAgentRounds(DEFAULT_MAX_ROUNDS);

                // Accumulate unique chunks across all rounds (first-occurrence wins)
                Map<String, RetrievalResult> mergedResults = new LinkedHashMap<>();
                List<RetrievalFeedback> feedbacks = new ArrayList<>();
                RetrievalPlan lastPlan = null;

                for (int round = 1; round <= maxRounds; round++) {
                    // 1. THINK — plan retrieval strategy
                    sink.next(StreamEvent.agentThinking(round, "Analyzing query..."));
                    PlanContext planCtx = new PlanContext(
                        request.query(), request.history(),
                        request.spaceConfig(), feedbacks);
                    RetrievalPlan plan = planner.plan(planCtx);
                    lastPlan = plan;

                    // 2. ACT — execute retrieval, accumulate with first-occurrence dedup
                    List<String> queryTexts = plan.subQueries().stream()
                        .map(SubQuery::rewrittenQuery).toList();
                    sink.next(StreamEvent.agentSearching(round, queryTexts));

                    List<RetrievalResult> roundResults = executor.execute(plan, request.filter());
                    for (RetrievalResult r : roundResults) {
                        mergedResults.putIfAbsent(r.chunkId(), r);
                    }

                    // 3. EVALUATE — check if accumulated results are sufficient
                    List<RetrievalResult> currentResults = new ArrayList<>(mergedResults.values());
                    EvaluationContext evalCtx = new EvaluationContext(
                        request.query(), plan.subQueries(),
                        currentResults, round, maxRounds,
                        request.spaceConfig());
                    EvaluationResult eval = evaluator.evaluate(evalCtx);
                    sink.next(StreamEvent.agentEvaluating(round, eval.sufficient()));

                    if (eval.sufficient() || round == maxRounds) {
                        break;
                    }

                    feedbacks.add(new RetrievalFeedback(
                        round, eval.missingAspects(), eval.suggestedNextQueries()));
                }

                // 4. UNIFIED RERANK — single call after all rounds
                List<RetrievalResult> allUnique = new ArrayList<>(mergedResults.values());
                if (!allUnique.isEmpty() && lastPlan != null) {
                    // Use canonical rewritten query (pronoun references are resolved)
                    String canonicalQuery = lastPlan.subQueries().get(0).rewrittenQuery();
                    allUnique = applyRerank(canonicalQuery, allUnique);
                }

                // 5. GENERATE — stream answer with citations
                GenerationContext genCtx = new GenerationContext(
                    request.query(), request.history(),
                    allUnique, request.spaceLanguage());
                generator.generateStream(genCtx)
                    .doOnNext(sink::next)
                    .doOnComplete(sink::complete)
                    .doOnError(sink::error)
                    .subscribe();

            } catch (Exception e) {
                try {
                    sink.next(StreamEvent.error("AGENT_ERROR", e.getMessage()));
                } catch (Exception ignored) {
                }
                sink.complete();
            }
        });
    }

    private List<RetrievalResult> applyRerank(String query, List<RetrievalResult> results) {
        List<String> contents = results.stream().map(RetrievalResult::content).toList();
        int topN = Math.min(results.size(), 10);
        List<RerankPort.RerankResult> reranked = rerankPort.rerank(query, contents, topN);
        return reranked.stream()
            .map(rr -> results.get(rr.index()))
            .toList();
    }
}
```

**Note:** `EvaluationContext` now requires `spaceConfig` as a new field — update that record too:

Modify `rag-domain/src/main/java/com/rag/domain/conversation/agent/model/EvaluationContext.java`:

```java
package com.rag.domain.conversation.agent.model;

import com.rag.domain.identity.model.RetrievalConfig;
import java.util.List;

public record EvaluationContext(
    String originalQuery,
    List<SubQuery> executedQueries,
    List<RetrievalResult> results,
    int currentRound,
    int maxRounds,
    RetrievalConfig spaceConfig
) {}
```

- [ ] **Step 4: Run tests**

```bash
mvn test -pl rag-domain -Dtest=AgentOrchestratorRerankTest -q
```
Expected: All 3 tests PASS.

- [ ] **Step 5: Verify full compile (EvaluationContext change affects LlmRetrievalEvaluator)**

```bash
mvn install -pl rag-domain -DskipTests -q
mvn compile -pl rag-application -q
```
Expected: BUILD SUCCESS. `LlmRetrievalEvaluator.evaluate(EvaluationContext)` still compiles — it just has access to one more field it doesn't use yet (Plan 3 will use it).

- [ ] **Step 6: Commit**

```bash
git add rag-domain/src/main/java/com/rag/domain/conversation/agent/AgentOrchestrator.java \
        rag-domain/src/main/java/com/rag/domain/conversation/agent/model/EvaluationContext.java \
        rag-domain/src/test/java/com/rag/domain/conversation/agent/AgentOrchestratorRerankTest.java
git commit -m "feat: unified end-of-loop rerank with canonical query and putIfAbsent dedup"
```

---

## Verification

- [ ] **Run full module test suite**

```bash
mvn test -pl rag-domain,rag-adapter-outbound -q
```
Expected: BUILD SUCCESS, no test failures.
