# Evaluator Early-Stopping Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `LlmRetrievalEvaluator`'s single `sufficient` check with 6-priority multi-criteria early-stopping logic, including a configurable score-based fast-path that avoids unnecessary LLM calls.

**Architecture:** `EvaluationContext` already includes `spaceConfig` (added in Plan 2). Rewrite `LlmRetrievalEvaluator.evaluate()` to check conditions in strict priority order: max-rounds force-pass → score fast-path → empty results → LLM evaluation → LLM-failure degraded pass → LLM-failure retry.

**Tech Stack:** Java 21, JUnit 5, Mockito, Maven (`rag-application`)

**Spec:** `docs/superpowers/specs/2026-04-02-agent-loop-retrieval-enhancement-design.md` — Section 3

**Execution order note:** Requires Plan 1 (for `RetrievalConfig` new fields) and Plan 2 (for `EvaluationContext.spaceConfig`). Independent of Plan 4.

---

## Chunk 1: Evaluator Rewrite

### Task 1: Rewrite LlmRetrievalEvaluator with 6-priority logic

**Files:**
- Modify: `rag-application/src/main/java/com/rag/application/agent/LlmRetrievalEvaluator.java`
- Create: `rag-application/src/test/java/com/rag/application/agent/LlmRetrievalEvaluatorTest.java`

- [ ] **Step 1: Write failing tests — one per priority condition**

Create `rag-application/src/test/java/com/rag/application/agent/LlmRetrievalEvaluatorTest.java`:

```java
package com.rag.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.conversation.port.LlmPort;
import com.rag.domain.identity.model.RetrievalConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmRetrievalEvaluatorTest {

    @Mock LlmPort llmPort;
    LlmRetrievalEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new LlmRetrievalEvaluator(llmPort, new ObjectMapper());
    }

    // Priority 1: max rounds reached → force sufficient, skip LLM
    @Test
    void priority1_maxRoundsReached_forcesSufficientWithoutLlmCall() {
        EvaluationContext ctx = context(List.of(), 3, 3, defaultConfig());
        EvaluationResult result = evaluator.evaluate(ctx);
        assertThat(result.sufficient()).isTrue();
        verifyNoInteractions(llmPort);
    }

    // Priority 2: fast-path — enough chunks AND high score (only when enableFastPath=true)
    @Test
    void priority2_fastPath_triggersWhenEnabledAndThresholdMet() {
        RetrievalConfig fastPathConfig = new RetrievalConfig(3, "semantic_header", "", 3, true, 3, 0.02);
        List<RetrievalResult> results = List.of(
            resultWithScore("c1", 0.025),  // above 0.02 threshold
            resultWithScore("c2", 0.020),
            resultWithScore("c3", 0.015)
        );
        EvaluationContext ctx = context(results, 1, 3, fastPathConfig);
        EvaluationResult result = evaluator.evaluate(ctx);
        assertThat(result.sufficient()).isTrue();
        verifyNoInteractions(llmPort);
    }

    @Test
    void priority2_fastPath_doesNotTriggerWhenDisabled() {
        RetrievalConfig noFastPath = new RetrievalConfig(3, "semantic_header", "", 3, false, 3, 0.02);
        List<RetrievalResult> results = List.of(
            resultWithScore("c1", 0.030),
            resultWithScore("c2", 0.025),
            resultWithScore("c3", 0.020)
        );
        when(llmPort.chat(any())).thenReturn(sufficientJson());
        EvaluationContext ctx = context(results, 1, 3, noFastPath);
        evaluator.evaluate(ctx);
        verify(llmPort, times(1)).chat(any()); // LLM was called
    }

    @Test
    void priority2_fastPath_doesNotTriggerWhenScoreBelowThreshold() {
        RetrievalConfig fastPathConfig = new RetrievalConfig(3, "semantic_header", "", 3, true, 3, 0.02);
        List<RetrievalResult> results = List.of(
            resultWithScore("c1", 0.010),  // below threshold
            resultWithScore("c2", 0.008),
            resultWithScore("c3", 0.005)
        );
        when(llmPort.chat(any())).thenReturn(sufficientJson());
        EvaluationContext ctx = context(results, 1, 3, fastPathConfig);
        evaluator.evaluate(ctx);
        verify(llmPort, times(1)).chat(any()); // LLM was called because threshold not met
    }

    // Priority 3: empty results → not sufficient
    @Test
    void priority3_emptyResults_returnsNotSufficient() {
        EvaluationContext ctx = context(List.of(), 1, 3, defaultConfig());
        EvaluationResult result = evaluator.evaluate(ctx);
        assertThat(result.sufficient()).isFalse();
        verifyNoInteractions(llmPort);
    }

    // Priority 4: LLM evaluation succeeds
    @Test
    void priority4_llmSaysSufficient_returnsSufficient() {
        when(llmPort.chat(any())).thenReturn(sufficientJson());
        EvaluationContext ctx = context(List.of(resultWithScore("c1", 0.01)), 1, 3, defaultConfig());
        EvaluationResult result = evaluator.evaluate(ctx);
        assertThat(result.sufficient()).isTrue();
    }

    @Test
    void priority4_llmSaysInsufficient_returnsNotSufficient() {
        when(llmPort.chat(any())).thenReturn("""
            {"sufficient":false,"reasoning":"missing details","missing_aspects":["aspect A"],"suggested_next_queries":["query B"]}
            """);
        EvaluationContext ctx = context(List.of(resultWithScore("c1", 0.01)), 1, 3, defaultConfig());
        EvaluationResult result = evaluator.evaluate(ctx);
        assertThat(result.sufficient()).isFalse();
        assertThat(result.missingAspects()).containsExactly("aspect A");
        assertThat(result.suggestedNextQueries()).containsExactly("query B");
    }

    // Priority 5: LLM fails AND results >= 3 → degraded sufficient
    @Test
    void priority5_llmFailsWithEnoughResults_degradedSufficient() {
        when(llmPort.chat(any())).thenThrow(new RuntimeException("LLM timeout"));
        List<RetrievalResult> results = List.of(
            resultWithScore("c1", 0.01),
            resultWithScore("c2", 0.01),
            resultWithScore("c3", 0.01)
        );
        EvaluationContext ctx = context(results, 1, 3, defaultConfig());
        EvaluationResult result = evaluator.evaluate(ctx);
        assertThat(result.sufficient()).isTrue();
    }

    // Priority 6: LLM fails AND results < 3 → not sufficient, retry
    @Test
    void priority6_llmFailsWithFewResults_notSufficient() {
        when(llmPort.chat(any())).thenThrow(new RuntimeException("LLM timeout"));
        List<RetrievalResult> results = List.of(resultWithScore("c1", 0.01));
        EvaluationContext ctx = context(results, 1, 3, defaultConfig());
        EvaluationResult result = evaluator.evaluate(ctx);
        assertThat(result.sufficient()).isFalse();
    }

    // --- helpers ---

    private EvaluationContext context(List<RetrievalResult> results,
                                       int currentRound, int maxRounds,
                                       RetrievalConfig config) {
        return new EvaluationContext("test query", List.of(), results, currentRound, maxRounds, config);
    }

    private RetrievalConfig defaultConfig() {
        return new RetrievalConfig(); // enableFastPath=false, minSufficientChunks=5, rawScoreThreshold=0.02
    }

    private RetrievalResult resultWithScore(String chunkId, double score) {
        return new RetrievalResult(chunkId, "doc-1", "Title", "content " + chunkId, 1, "sec", score, Map.of());
    }

    private String sufficientJson() {
        return """
            {"sufficient":true,"reasoning":"good coverage","missing_aspects":[],"suggested_next_queries":[]}
            """;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -pl rag-application -Dtest=LlmRetrievalEvaluatorTest -q
```
Expected: FAIL — current evaluator does not implement the 6-priority logic.

- [ ] **Step 3: Rewrite LlmRetrievalEvaluator**

Replace the full contents of `rag-application/src/main/java/com/rag/application/agent/LlmRetrievalEvaluator.java`:

```java
package com.rag.application.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.domain.conversation.agent.RetrievalEvaluator;
import com.rag.domain.conversation.agent.model.EvaluationContext;
import com.rag.domain.conversation.agent.model.EvaluationResult;
import com.rag.domain.conversation.agent.model.RetrievalResult;
import com.rag.domain.conversation.port.LlmPort;
import com.rag.domain.identity.model.RetrievalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LlmRetrievalEvaluator implements RetrievalEvaluator {

    private static final Logger log = LoggerFactory.getLogger(LlmRetrievalEvaluator.class);
    private static final int DEGRADED_PASS_MIN_CHUNKS = 3;

    private final LlmPort llmPort;
    private final ObjectMapper objectMapper;

    public LlmRetrievalEvaluator(LlmPort llmPort, ObjectMapper objectMapper) {
        this.llmPort = llmPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        // Priority 1: max rounds reached — force sufficient, never call LLM
        if (context.currentRound() >= context.maxRounds()) {
            return new EvaluationResult(true, "Max rounds reached", List.of(), List.of());
        }

        // Priority 2: score-based fast-path (skips LLM call)
        RetrievalConfig config = context.spaceConfig();
        if (config.enableFastPath() && !context.results().isEmpty()) {
            double topScore = context.results().stream()
                .mapToDouble(RetrievalResult::score)
                .max()
                .orElse(0.0);
            if (context.results().size() >= config.minSufficientChunks()
                    && topScore >= config.rawScoreThreshold()) {
                log.debug("Fast-path early stop: {} chunks, top score {}", context.results().size(), topScore);
                return new EvaluationResult(true, "Fast-path: score threshold met", List.of(), List.of());
            }
        }

        // Priority 3: no results — not sufficient
        if (context.results().isEmpty()) {
            return new EvaluationResult(false, "No results found",
                List.of("entire query"), List.of(context.originalQuery()));
        }

        // Priority 4 + 5 + 6: LLM evaluation
        try {
            String response = llmPort.chat(buildLlmRequest(context));
            return parseEvalResponse(response, context.originalQuery());
        } catch (Exception e) {
            log.warn("Evaluator LLM call failed on round {}/{}: {}",
                context.currentRound(), context.maxRounds(), e.getMessage());

            // Priority 5: LLM failed but we have enough results — degrade gracefully
            if (context.results().size() >= DEGRADED_PASS_MIN_CHUNKS) {
                return new EvaluationResult(true,
                    "Evaluation failed, proceeding with " + context.results().size() + " results",
                    List.of(), List.of());
            }

            // Priority 6: LLM failed and too few results — retry next round
            return new EvaluationResult(false, "Evaluation failed: " + e.getMessage(),
                List.of("evaluation error"), List.of(context.originalQuery()));
        }
    }

    private LlmPort.LlmRequest buildLlmRequest(EvaluationContext context) {
        String systemPrompt = """
            You are a retrieval evaluator. Given a user question and retrieved knowledge chunks,
            determine if the retrieved information is sufficient to answer the question fully.

            Respond in JSON format only:
            {
              "sufficient": true/false,
              "reasoning": "why sufficient or not",
              "missing_aspects": ["aspect1", "aspect2"],
              "suggested_next_queries": ["query1", "query2"]
            }

            If sufficient, missing_aspects and suggested_next_queries should be empty arrays.
            """;

        StringBuilder userMessage = new StringBuilder();
        userMessage.append("User question: ").append(context.originalQuery()).append("\n\n");
        userMessage.append("Retrieved chunks (").append(context.results().size()).append(" total):\n");
        for (int i = 0; i < Math.min(context.results().size(), 8); i++) {
            var r = context.results().get(i);
            userMessage.append("---\n[").append(i + 1).append("] ")
                .append(r.documentTitle()).append(" | ").append(r.sectionPath()).append("\n")
                .append(r.content(), 0, Math.min(r.content().length(), 500)).append("\n");
        }

        return new LlmPort.LlmRequest(systemPrompt, List.of(), userMessage.toString(), 0.2);
    }

    private EvaluationResult parseEvalResponse(String response, String originalQuery) {
        try {
            String json = response;
            if (json.contains("```")) {
                int start = json.indexOf("{");
                int end = json.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    json = json.substring(start, end + 1);
                }
            }

            JsonNode root = objectMapper.readTree(json);
            boolean sufficient = root.has("sufficient") && root.get("sufficient").asBoolean();
            String reasoning = root.has("reasoning") ? root.get("reasoning").asText() : "";

            List<String> missingAspects = new ArrayList<>();
            if (root.has("missing_aspects") && root.get("missing_aspects").isArray()) {
                root.get("missing_aspects").forEach(n -> missingAspects.add(n.asText()));
            }

            List<String> suggestedQueries = new ArrayList<>();
            if (root.has("suggested_next_queries") && root.get("suggested_next_queries").isArray()) {
                root.get("suggested_next_queries").forEach(n -> suggestedQueries.add(n.asText()));
            }

            return new EvaluationResult(sufficient, reasoning, missingAspects, suggestedQueries);
        } catch (Exception e) {
            log.warn("Failed to parse evaluator response: {}", e.getMessage());
            return new EvaluationResult(false, "Parse failed: " + e.getMessage(),
                List.of("parse error"), List.of(originalQuery));
        }
    }
}
```

- [ ] **Step 4: Run all evaluator tests**

```bash
mvn test -pl rag-application -Dtest=LlmRetrievalEvaluatorTest -q
```
Expected: All 9 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add rag-application/src/main/java/com/rag/application/agent/LlmRetrievalEvaluator.java \
        rag-application/src/test/java/com/rag/application/agent/LlmRetrievalEvaluatorTest.java
git commit -m "feat: rewrite LlmRetrievalEvaluator with 6-priority early-stop and configurable RRF score fast-path"
```

---

## Verification

- [ ] **Run full module test suite**

```bash
mvn test -pl rag-application -q
```
Expected: BUILD SUCCESS, no test failures.
