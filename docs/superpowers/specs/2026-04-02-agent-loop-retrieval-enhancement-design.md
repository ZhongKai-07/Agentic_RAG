# Agent Loop Retrieval Enhancement Design

**Date:** 2026-04-02  
**Status:** Approved  
**Scope:** `rag-application` layer + minor `RetrievalConfig` additions in `rag-domain` + `LocalOpenSearchAdapter` null-guard in `rag-adapter-outbound`

## Overview

Improve retrieval quality and fault tolerance of the Agentic RAG ReAct loop. The current implementation has three main weaknesses:

1. **Planner** — fragile JSON parsing, no conversation history injection, no expansion bounds
2. **Cross-round result fusion** — rounds simply accumulate results in order; later rounds with better chunks can be buried
3. **Fault tolerance** — LLM timeouts silently degrade, OpenSearch failures can crash the loop or hang SSE connections

This design addresses all three through targeted changes to four files in `rag-application`, preserving the existing ReAct loop structure and all domain interfaces.

---

## Goals

- Sub-query expansion on every query (simple and complex), bounded by config
- Single unified Rerank call at the end of all rounds (replaces per-round Rerank)
- Aggressive Evaluator early-stopping to avoid unnecessary LLM rounds
- Explicit timeout protection on all LLM calls
- OpenSearch failure graceful degradation (embedding fallback → keyword-only; circuit break → use accumulated results)
- Frontend always receives a terminal SSE event — no hanging connections

## Non-Goals

- No QueryRouter / pre-classification of queries as SIMPLE vs COMPLEX
- No changes to `rag-domain` port interfaces or aggregate models (only `RetrievalConfig` config fields added)
- No functional changes to `rag-adapter-outbound` beyond a null-guard in `LocalOpenSearchAdapter.executeKnnSearch()` (single conditional, no logic change)
- No changes to database schema or OpenSearch index mapping
- No additional LLM calls beyond the existing Planner + Evaluator + Generator pattern

---

## Architecture

### Changed Files

| File | Change Type | Summary |
|------|------------|---------|
| `rag-application/.../agent/LlmRetrievalPlanner.java` | Enhanced | Robust JSON parsing, conversation history injection, bounded sub-query expansion, explicit no-repeat constraint |
| `rag-application/.../agent/HybridRetrievalExecutor.java` | Enhanced | Embedding fallback to keyword-only (delegates to adapter null-guard), per-sub-query failure isolation |
| `rag-application/.../agent/LlmRetrievalEvaluator.java` | Enhanced | Multi-criteria early-stop with corrected RRF score threshold |
| `rag-domain/.../identity/model/RetrievalConfig.java` | Minor | Add `maxSubQueries` and `enableFastPath` fields |
| `rag-domain/.../conversation/agent/AgentOrchestrator.java` | Enhanced | Unified end-of-loop Rerank with canonical query, best-score dedup simplified to putIfAbsent, refined exception handling |
| `rag-adapter-outbound/.../vectorstore/LocalOpenSearchAdapter.java` | Minor | Add null-guard on `queryVector` in `executeKnnSearch()` to enable keyword-only fallback |
| `rag-infrastructure/.../config/ServiceRegistryConfig.java` | Minor | Add `rag.services.llm.timeout-seconds` bound to `LlmProperties`; configure `RestClient` `ReadTimeout` |

### Unchanged

- All domain port interfaces (`LlmPort`, `EmbeddingPort`, `RerankPort`, `VectorStorePort`)
- `LlmAnswerGenerator` — receives better-ranked input, no internal changes needed
- `AgentOrchestrator` loop structure (ReAct skeleton preserved)

---

## Detailed Design

### 1. Planner Enhancement (`LlmRetrievalPlanner`)

#### 1.1 Robust JSON Parsing

Replace current `indexOf("{") / lastIndexOf("}")` string slicing with a three-step fallback:

1. Try `objectMapper.readTree(response)` directly
2. On failure, extract with regex `\{[\s\S]*\}`
3. On failure, use `fallbackPlan(originalQuery)`

#### 1.2 Conversation History Injection

Append the last N turns of `context.history()` to the system prompt:

```
Recent conversation:
User: <message>
Assistant: <message>
...
```

This enables the Planner to resolve pronoun references and rewrite queries with full context (e.g., "its price" → "Product X price").

#### 1.3 Bounded Sub-Query Expansion

Add `maxSubQueries` to `RetrievalConfig` (default: 3). Pass the bound into the Planner prompt:

```
Generate 1 to {maxSubQueries} sub-queries.
- Use 1 sub-query for focused, single-concept questions.
- Use 2–{maxSubQueries} sub-queries for multi-aspect or comparative questions.
Do NOT exceed {maxSubQueries} sub-queries.
```

After parsing, enforce the bound in code:
```java
subQueries = subQueries.subList(0, Math.min(subQueries.size(), maxSubQueries));
```

#### 1.4 Optimized Planner Prompt Structure

```
[Role]
You are a retrieval planner for a RAG system. Decompose the user question into optimal search sub-queries.

[Conversation History]
{last N turns, if any — used to resolve pronoun references and add context}

[Previous Attempts in This Session]
{if round > 1, list each prior sub-query and the missing aspects reported by the Evaluator}
- Round 1 queries: ["..."], missing: ["..."]
Constraint: Do NOT repeat the exact same queries listed above.

[Constraints]
- Generate 1 to {maxSubQueries} sub-queries
- Rewrite queries: expand abbreviations, resolve pronoun references, add domain context
- Each sub-query must have a distinct intent — avoid redundant queries
- Output strict JSON only, no markdown fences

[Output Format]
{
  "sub_queries": [
    {"rewritten_query": "...", "intent": "..."}
  ],
  "strategy": "HYBRID",
  "top_k": 10
}
```

The `[Previous Attempts]` section is populated from `RetrievalFeedback` already collected by `AgentOrchestrator`. For round 1 this section is omitted entirely.

---

### 2. Unified End-of-Loop Rerank (`AgentOrchestrator`)

#### 2.1 Remove Per-Round Rerank

Remove the `applyRerank()` call from inside the round loop. Raw OpenSearch hybrid scores are sufficient for intermediate Evaluator decisions.

#### 2.2 Accumulate with First-Occurrence Deduplication

During each round, merge results into a `Map<String, RetrievalResult>` keyed by `chunkId`, keeping the first occurrence when the same chunk appears in multiple rounds:

```java
for (RetrievalResult r : roundResults) {
    mergedResults.putIfAbsent(r.chunkId(), r);
}
```

Score comparison across rounds is meaningless: BM25/KNN raw scores are query-dependent (different queries produce incomparable absolute values), and RRF scores are rank-relative within a single query. Since all accumulated results go through a unified Rerank at the end — which is the authoritative ranking — the version of a duplicate chunk we retain makes no practical difference.

#### 2.3 Single Rerank After All Rounds

After the loop exits, apply Rerank once to all deduplicated results using the **canonical rewritten query** from the last round's plan (not the raw user query):

```java
List<RetrievalResult> allUnique = new ArrayList<>(mergedResults.values());
if (!allUnique.isEmpty()) {
    // Use the last Planner's primary rewritten query for reranking,
    // so pronoun references are resolved (e.g. "its price" → "Product X price")
    String canonicalQuery = lastPlan.subQueries().get(0).rewrittenQuery();
    allUnique = applyRerank(canonicalQuery, allUnique);
}
```

`AgentOrchestrator` stores the `RetrievalPlan` from each round; the last round's plan is used after the loop.

**Benefits vs per-round Rerank:**
- Rerank API calls: max 3 → 1
- Reranker sees all candidates globally, producing better relative rankings
- Canonical rewritten query ensures multi-turn references are resolved before reranking

---

### 3. Evaluator Early-Stopping (`LlmRetrievalEvaluator`)

Replace single `sufficient` check with multi-criteria evaluation (evaluated in order):

| Priority | Condition | Action |
|----------|-----------|--------|
| 1 | `currentRound >= maxRounds` | Force sufficient, skip LLM call |
| 2 | `results.size() >= minSufficientChunks` AND `topScore >= rawScoreThreshold` | Fast-path sufficient (skip LLM call) — only when `enableFastPath=true` in RetrievalConfig |
| 3 | `results.isEmpty()` | Not sufficient, add feedback |
| 4 | LLM evaluation succeeds | Use `sufficient` from response |
| 5 | LLM call times out or fails AND `results.size() >= 3` | Degraded sufficient (proceed with current results) |
| 6 | LLM call fails AND `results.size() < 3` | Not sufficient, retry next round |

**New config fields in `RetrievalConfig`:**
- `minSufficientChunks` (default: 5)
- `rawScoreThreshold` (default: **0.02**) — RRF score threshold. `LocalOpenSearchAdapter` returns RRF scores in the range `(0, ~0.033]` (formula: `Σ 1/(60+rank)`; max ≈ 0.033 when ranked first in both BM25 and KNN). A threshold of 0.02 selects chunks ranked in the top 3 of at least one retrieval stream.
- `enableFastPath` (default: false) — space-level toggle for score-based early stop

---

### 4. Fault Tolerance

#### 4.1 LLM Timeout Protection

`AliCloudLlmAdapter.chat()` uses Spring AI `ChatClient.call()` which is **synchronous blocking**. `CompletableFuture.orTimeout()` only cancels the waiting caller — it does NOT interrupt the blocked HTTP thread. Under high concurrency this exhausts the thread pool.

**Correct approach: configure timeout at the HTTP client level.**

Add `rag.services.llm.timeout-seconds` (default: 30) to `ServiceRegistryConfig.LlmProperties`. In `rag-infrastructure`, configure the `RestClient` (or `WebClient`) bean used by Spring AI's `ChatClient` with `ReadTimeout`:

```java
// In infrastructure config (e.g., AgentConfig or a new LlmClientConfig)
RestClient restClient = RestClient.builder()
    .requestFactory(factory -> factory.setReadTimeout(
        Duration.ofSeconds(llmProperties.getTimeoutSeconds())))
    .build();
// Pass to ChatClient.Builder via requestFactory
```

This ensures the HTTP connection itself closes after the timeout, freeing the thread.

Fallback behavior per component (unchanged — now reliably triggered by actual timeout):

| Component | Timeout / Failure Behavior |
|-----------|---------------------------|
| Planner | Fall back to original query as single sub-query |
| Evaluator | If results ≥ 3: mark sufficient and exit. Otherwise: mark insufficient, continue to next round |
| Generator (streaming) | Use `onErrorResume` on the `Flux` to emit `StreamEvent.error("GENERATOR_TIMEOUT", message)` then complete — ensures SSE connection always terminates |

#### 4.2 OpenSearch Graceful Degradation (`HybridRetrievalExecutor`)

**Embedding failure → keyword-only fallback:**

When `embeddingPort.embed()` throws, catch the exception, log a warning, and call `vectorStorePort.hybridSearch()` with `queryVector = null`.

`VectorStorePort.HybridSearchRequest.queryVector` is `float[]` — `null` is a valid Java value but its semantics must be defined explicitly. **`LocalOpenSearchAdapter.executeKnnSearch()` currently has no null-guard and will throw NPE.** The adapter requires the following change:

```java
// LocalOpenSearchAdapter.executeKnnSearch() — add null guard
private List<SearchHit> executeKnnSearch(String indexName, HybridSearchRequest request,
                                          List<Query> filterQueries) throws IOException {
    if (request.queryVector() == null) {
        return List.of();  // skip KNN, BM25 results are used alone
    }
    // ... existing KNN logic unchanged
}
```

This is the only change to `rag-adapter-outbound`. When `queryVector` is null, `mergeWithRRF` receives an empty KNN list and returns BM25-only results.

**Sub-query isolation:**

Each sub-query executes in its own try-catch. A failure in one sub-query does not abort the others. `index_not_found_exception` remains fatal (throws `KnowledgeBaseEmptyException`) as the index will not recover within the request.

#### 4.3 Orchestrator Exception Boundary

Refine the catch block in `orchestrate()` to distinguish failure types:

```java
} catch (KnowledgeBaseEmptyException e) {
    if (!mergedResults.isEmpty()) {
        // Partial results available — proceed to Generate
        generateFromAccumulatedResults(mergedResults, request, sink);
    } else {
        sink.next(StreamEvent.error("KNOWLEDGE_BASE_EMPTY", e.getMessage()));
        sink.complete();
    }
} catch (InterruptedException | TimeoutException e) {
    sink.next(StreamEvent.error("AGENT_TIMEOUT", "Request timed out"));
    sink.complete();
} catch (Exception e) {
    sink.next(StreamEvent.error("AGENT_ERROR", e.getMessage()));
    sink.complete();
}
```

**Guarantee:** the SSE stream always terminates with either `done` or `error` — never hangs.

---

## Configuration Reference

All new config fields added to `RetrievalConfig` (domain model) and bound via `rag.services.*` YAML:

| Field | Default | Description |
|-------|---------|-------------|
| `maxSubQueries` | 3 | Max sub-queries Planner may generate per round |
| `enableFastPath` | false | Enable score-threshold early stop in Evaluator |
| `minSufficientChunks` | 5 | Min chunk count for fast-path early stop |
| `rawScoreThreshold` | 0.02 | Min top-chunk RRF score for fast-path early stop (RRF range: 0~0.033) |

LLM timeout in `ServiceRegistryConfig.LlmProperties` (bound from YAML, applied to `RestClient` `ReadTimeout` in infrastructure config):

| Field | Default | Description |
|-------|---------|-------------|
| `rag.services.llm.timeout-seconds` | 30 | HTTP ReadTimeout for synchronous LLM calls (Planner, Evaluator). Configured at RestClient level — not via CompletableFuture wrapper. |

---

## Data Flow (Updated)

```
orchestrate(AgentRequest)
│
├─ Round 1..N:
│   ├─ [THINK]    LlmRetrievalPlanner.plan()              ← LLM call (HTTP ReadTimeout)
│   │              bounded sub-queries, history-aware
│   │              [Previous Attempts] injected for round > 1 (no-repeat constraint)
│   │              fallback: original query if LLM fails
│   │
│   ├─ [ACT]      HybridRetrievalExecutor.execute()       ← OpenSearch hybrid search
│   │              embed() failure → queryVector=null → BM25-only (adapter null-guard)
│   │              putIfAbsent dedup into mergedResults
│   │
│   └─ [EVAL]     LlmRetrievalEvaluator.evaluate()        ← LLM call (HTTP ReadTimeout) or skipped
│                  6-priority multi-criteria early stop
│                  fast-path: RRF topScore >= 0.02 AND count >= 5 (if enableFastPath=true)
│
├─ Post-loop:
│   └─ applyRerank(canonicalQuery, mergedResults)          ← Single Rerank API call
│                  canonicalQuery = lastPlan.subQueries().get(0).rewrittenQuery()
│
└─ [GENERATE]  LlmAnswerGenerator.generateStream()        ← Streaming LLM call
                emits: agent_thinking, content_delta, citation, done
                onErrorResume → StreamEvent.error + complete (no hanging SSE)
```

---

## Testing Considerations

- Unit test `LlmRetrievalPlanner` JSON parsing with malformed LLM responses (missing braces, markdown fences, empty arrays)
- Unit test `LlmRetrievalPlanner`: round > 1 prompt must contain `[Previous Attempts]` section with prior queries; assert "Do NOT repeat" constraint present
- Unit test `AgentOrchestrator` dedup: same chunkId in round 1 and round 2 — verify `putIfAbsent` keeps round 1 entry
- Unit test `AgentOrchestrator` canonical query: last round's `plan.subQueries().get(0).rewrittenQuery()` is passed to `applyRerank()`, not raw user query
- Unit test Evaluator early-stop conditions (each of the 6 priority conditions independently)
- Unit test fast-path threshold: RRF score of 0.02 triggers fast-path; score of 0.01 does not
- Unit test `LocalOpenSearchAdapter.executeKnnSearch()` with `queryVector = null` — verify returns empty list without NPE
- Integration test: simulate `embeddingPort.embed()` throwing — verify BM25-only fallback produces results via `mergeWithRRF` with empty KNN list
- Integration test: simulate Planner LLM `ReadTimeout` — verify loop continues with fallback plan (no thread leak)
- Integration test: simulate all sub-queries failing — verify `error` SSE event emitted and connection closes cleanly
- Integration test: Generator `Flux` error mid-stream — verify `onErrorResume` emits `StreamEvent.error` and SSE terminates (no hanging connection)
