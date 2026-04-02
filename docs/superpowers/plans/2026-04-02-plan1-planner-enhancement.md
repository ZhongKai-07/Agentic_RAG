# Planner Enhancement Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance `LlmRetrievalPlanner` with robust JSON parsing, conversation history awareness, bounded sub-query expansion, and explicit no-repeat constraints across rounds — and lay the shared `RetrievalConfig` foundation used by Plans 3 and 4.

**Architecture:** Add 4 new fields to `RetrievalConfig` (record expansion + SpaceMapper update for JSONB backward compatibility), then rewrite `LlmRetrievalPlanner.plan()` to use three-step JSON fallback, inject history into the system prompt, enforce `maxSubQueries` bound in code, and inject `[Previous Attempts]` into round 2+ prompts.

**Tech Stack:** Java 21 records, JUnit 5, Mockito, Maven multi-module (`rag-domain`, `rag-adapter-outbound`, `rag-application`)

**Spec:** `docs/superpowers/specs/2026-04-02-agent-loop-retrieval-enhancement-design.md` — Section 1

**Execution order note:** This plan must run before Plan 3 (Evaluator), as it adds `RetrievalConfig` fields that Plan 3 depends on. Plan 2 (Unified Rerank) and Plan 4 (Fault Tolerance) are independent.

---

## Chunk 1: RetrievalConfig Foundation

### Task 1: Expand RetrievalConfig and update SpaceMapper

**Files:**
- Modify: `rag-domain/src/main/java/com/rag/domain/identity/model/RetrievalConfig.java`
- Modify: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/SpaceMapper.java`

- [ ] **Step 1: Write failing test for new RetrievalConfig fields**

Create `rag-domain/src/test/java/com/rag/domain/identity/model/RetrievalConfigTest.java`:

```java
package com.rag.domain.identity.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RetrievalConfigTest {

    @Test
    void defaultConstructor_setsExpectedDefaults() {
        RetrievalConfig config = new RetrievalConfig();
        assertThat(config.maxAgentRounds()).isEqualTo(3);
        assertThat(config.maxSubQueries()).isEqualTo(3);
        assertThat(config.enableFastPath()).isFalse();
        assertThat(config.minSufficientChunks()).isEqualTo(5);
        assertThat(config.rawScoreThreshold()).isEqualTo(0.02);
    }

    @Test
    void maxSubQueries_returnsDefaultWhenZero() {
        RetrievalConfig config = new RetrievalConfig(3, "semantic_header", "", 0, false, 5, 0.02);
        assertThat(config.maxSubQueries(3)).isEqualTo(3);
    }

    @Test
    void maxSubQueries_returnsConfiguredValueWhenPositive() {
        RetrievalConfig config = new RetrievalConfig(3, "semantic_header", "", 2, false, 5, 0.02);
        assertThat(config.maxSubQueries(3)).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -pl rag-domain -Dtest=RetrievalConfigTest -q
```
Expected: FAIL — `RetrievalConfig` has no `maxSubQueries` field yet.

- [ ] **Step 3: Rewrite RetrievalConfig with all new fields**

Replace `rag-domain/src/main/java/com/rag/domain/identity/model/RetrievalConfig.java`:

```java
package com.rag.domain.identity.model;

public record RetrievalConfig(
    int maxAgentRounds,
    String chunkingStrategy,
    String metadataExtractionPrompt,
    int maxSubQueries,
    boolean enableFastPath,
    int minSufficientChunks,
    double rawScoreThreshold
) {
    public RetrievalConfig() {
        this(3, "semantic_header", "", 3, false, 5, 0.02);
    }

    public int maxAgentRounds(int defaultValue) {
        return maxAgentRounds > 0 ? maxAgentRounds : defaultValue;
    }

    public int maxSubQueries(int defaultValue) {
        return maxSubQueries > 0 ? maxSubQueries : defaultValue;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -pl rag-domain -Dtest=RetrievalConfigTest -q
```
Expected: PASS

- [ ] **Step 5: Update SpaceMapper to handle new fields (with JSONB backward-compatible defaults)**

In `SpaceMapper.toDomain()`, replace the `RetrievalConfig` construction block:

```java
// OLD
s.setRetrievalConfig(new RetrievalConfig(
    rc.getOrDefault("maxAgentRounds", 3) instanceof Number n ? n.intValue() : 3,
    (String) rc.getOrDefault("chunkingStrategy", "semantic_header"),
    (String) rc.getOrDefault("metadataExtractionPrompt", "")
));

// NEW
s.setRetrievalConfig(new RetrievalConfig(
    rc.getOrDefault("maxAgentRounds", 3) instanceof Number n ? n.intValue() : 3,
    (String) rc.getOrDefault("chunkingStrategy", "semantic_header"),
    (String) rc.getOrDefault("metadataExtractionPrompt", ""),
    rc.getOrDefault("maxSubQueries", 3) instanceof Number n ? n.intValue() : 3,
    rc.getOrDefault("enableFastPath", false) instanceof Boolean b && b,
    rc.getOrDefault("minSufficientChunks", 5) instanceof Number n ? n.intValue() : 5,
    rc.getOrDefault("rawScoreThreshold", 0.02) instanceof Number n ? n.doubleValue() : 0.02
));
```

In `SpaceMapper.toEntity()`, replace the map building block:

```java
// OLD
map.put("maxAgentRounds", rc.maxAgentRounds());
map.put("chunkingStrategy", rc.chunkingStrategy());
map.put("metadataExtractionPrompt", rc.metadataExtractionPrompt());

// NEW
map.put("maxAgentRounds", rc.maxAgentRounds());
map.put("chunkingStrategy", rc.chunkingStrategy());
map.put("metadataExtractionPrompt", rc.metadataExtractionPrompt());
map.put("maxSubQueries", rc.maxSubQueries());
map.put("enableFastPath", rc.enableFastPath());
map.put("minSufficientChunks", rc.minSufficientChunks());
map.put("rawScoreThreshold", rc.rawScoreThreshold());
```

- [ ] **Step 6: Verify full build compiles (RetrievalConfig is used across modules)**

```bash
mvn install -pl rag-domain -DskipTests -q
mvn compile -pl rag-adapter-outbound,rag-application -q
```
Expected: BUILD SUCCESS for both commands. The `install` step publishes the updated `rag-domain` artifact to the local Maven repo so downstream modules pick up the new `RetrievalConfig` constructor.

- [ ] **Step 7: Commit**

```bash
git add rag-domain/src/main/java/com/rag/domain/identity/model/RetrievalConfig.java \
        rag-domain/src/test/java/com/rag/domain/identity/model/RetrievalConfigTest.java \
        rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/SpaceMapper.java
git commit -m "feat: expand RetrievalConfig with maxSubQueries, enableFastPath, minSufficientChunks, rawScoreThreshold"
```

---

## Chunk 2: Planner Enhancement

### Task 2: Robust JSON parsing

**Files:**
- Modify: `rag-application/src/main/java/com/rag/application/agent/LlmRetrievalPlanner.java`
- Create: `rag-application/src/test/java/com/rag/application/agent/LlmRetrievalPlannerTest.java`

- [ ] **Step 1: Write failing tests for JSON parsing**

Create `rag-application/src/test/java/com/rag/application/agent/LlmRetrievalPlannerTest.java`:

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmRetrievalPlannerTest {

    @Mock LlmPort llmPort;
    LlmRetrievalPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new LlmRetrievalPlanner(llmPort, new ObjectMapper());
    }

    @Test
    void plan_parsesCleanJson() {
        when(llmPort.chat(any())).thenReturn("""
            {"sub_queries":[{"rewritten_query":"test query","intent":"search"}],"strategy":"HYBRID","top_k":10}
            """);
        RetrievalPlan plan = planner.plan(planContext("test query"));
        assertThat(plan.subQueries()).hasSize(1);
        assertThat(plan.subQueries().get(0).rewrittenQuery()).isEqualTo("test query");
    }

    @Test
    void plan_parsesJsonWrappedInMarkdownFence() {
        when(llmPort.chat(any())).thenReturn("""
            ```json
            {"sub_queries":[{"rewritten_query":"fenced query","intent":"search"}],"strategy":"HYBRID","top_k":10}
            ```
            """);
        RetrievalPlan plan = planner.plan(planContext("original"));
        assertThat(plan.subQueries()).hasSize(1);
        assertThat(plan.subQueries().get(0).rewrittenQuery()).isEqualTo("fenced query");
    }

    @Test
    void plan_fallsBackToOriginalQueryOnMalformedJson() {
        when(llmPort.chat(any())).thenReturn("not json at all");
        RetrievalPlan plan = planner.plan(planContext("original query"));
        assertThat(plan.subQueries()).hasSize(1);
        assertThat(plan.subQueries().get(0).rewrittenQuery()).isEqualTo("original query");
    }

    @Test
    void plan_fallsBackWhenLlmThrows() {
        when(llmPort.chat(any())).thenThrow(new RuntimeException("LLM unavailable"));
        RetrievalPlan plan = planner.plan(planContext("original query"));
        assertThat(plan.subQueries()).hasSize(1);
        assertThat(plan.subQueries().get(0).rewrittenQuery()).isEqualTo("original query");
    }

    @Test
    void plan_enforcesMaxSubQueriesBound() {
        when(llmPort.chat(any())).thenReturn("""
            {"sub_queries":[
              {"rewritten_query":"q1","intent":"i1"},
              {"rewritten_query":"q2","intent":"i2"},
              {"rewritten_query":"q3","intent":"i3"},
              {"rewritten_query":"q4","intent":"i4"}
            ],"strategy":"HYBRID","top_k":10}
            """);
        // maxSubQueries=2 in the config
        RetrievalPlan plan = planner.plan(planContextWithMaxSubQueries("multi-aspect query", 2));
        assertThat(plan.subQueries()).hasSize(2);
    }

    @Test
    void plan_promptContainsPreviousAttemptsOnRound2() {
        // Capture the LlmRequest to inspect the prompt
        com.rag.domain.conversation.port.LlmPort.LlmRequest[] captured = new LlmPort.LlmRequest[1];
        when(llmPort.chat(any())).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return """
                {"sub_queries":[{"rewritten_query":"retry query","intent":"retry"}],"strategy":"HYBRID","top_k":10}
                """;
        });

        List<RetrievalFeedback> feedbacks = List.of(
            new RetrievalFeedback(1, List.of("missing aspect X"), List.of("suggested query"))
        );
        PlanContext ctx = new PlanContext("original", List.of(),
            new RetrievalConfig(), feedbacks);
        planner.plan(ctx);

        assertThat(captured[0].systemPrompt()).contains("[Previous Attempts");
        assertThat(captured[0].systemPrompt()).contains("Do NOT repeat");
        assertThat(captured[0].systemPrompt()).contains("missing aspect X");
    }

    // helpers
    private PlanContext planContext(String query) {
        return new PlanContext(query, List.of(), new RetrievalConfig(), List.of());
    }

    private PlanContext planContextWithMaxSubQueries(String query, int max) {
        RetrievalConfig config = new RetrievalConfig(3, "semantic_header", "", max, false, 5, 0.02);
        return new PlanContext(query, List.of(), config, List.of());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -pl rag-application -Dtest=LlmRetrievalPlannerTest -q
```
Expected: FAIL — tests for markdown fence parsing, maxSubQueries, and Previous Attempts don't pass yet.

- [ ] **Step 3: Rewrite LlmRetrievalPlanner**

Replace the full contents of `rag-application/src/main/java/com/rag/application/agent/LlmRetrievalPlanner.java`:

```java
package com.rag.application.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.domain.conversation.agent.RetrievalPlanner;
import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.conversation.port.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmRetrievalPlanner implements RetrievalPlanner {

    private static final Logger log = LoggerFactory.getLogger(LlmRetrievalPlanner.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");
    private static final int DEFAULT_MAX_SUB_QUERIES = 3;

    private final LlmPort llmPort;
    private final ObjectMapper objectMapper;

    public LlmRetrievalPlanner(LlmPort llmPort, ObjectMapper objectMapper) {
        this.llmPort = llmPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public RetrievalPlan plan(PlanContext context) {
        int maxSubQueries = context.spaceConfig().maxSubQueries(DEFAULT_MAX_SUB_QUERIES);
        String systemPrompt = buildPlannerPrompt(context, maxSubQueries);
        String userMessage = buildUserMessage(context);

        try {
            String response = llmPort.chat(new LlmPort.LlmRequest(
                systemPrompt,
                context.history(),
                userMessage,
                0.3
            ));
            return parsePlanResponse(response, context.userQuery(), maxSubQueries);
        } catch (Exception e) {
            log.warn("Planner LLM call failed, using fallback: {}", e.getMessage());
            return fallbackPlan(context.userQuery());
        }
    }

    private String buildUserMessage(PlanContext context) {
        // On round 2+, include previous feedback in the user message
        if (context.feedback() != null && !context.feedback().isEmpty()) {
            RetrievalFeedback latest = context.feedback().get(context.feedback().size() - 1);
            return "Original query: " + context.userQuery()
                + "\n\nPrevious retrieval missed these aspects: " + latest.missingAspects()
                + "\nSuggested queries: " + latest.suggestedNextQueries();
        }
        return context.userQuery();
    }

    private String buildPlannerPrompt(PlanContext context, int maxSubQueries) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            [Role]
            You are a retrieval planner for a RAG system. Decompose the user question into optimal search sub-queries.

            """);

        // Inject conversation history if available
        if (context.history() != null && !context.history().isEmpty()) {
            prompt.append("[Conversation History]\n");
            for (LlmPort.ChatMessage msg : context.history()) {
                String role = "user".equals(msg.role()) ? "User" : "Assistant";
                prompt.append(role).append(": ").append(msg.content()).append("\n");
            }
            prompt.append("\n");
        }

        // Inject previous attempts on round 2+
        if (context.feedback() != null && !context.feedback().isEmpty()) {
            prompt.append("[Previous Attempts in This Session]\n");
            for (RetrievalFeedback fb : context.feedback()) {
                prompt.append("- Round ").append(fb.round())
                    .append(" queries attempted, missing: ").append(fb.missingAspects())
                    .append(", suggested: ").append(fb.suggestedNextQueries()).append("\n");
            }
            prompt.append("Constraint: Do NOT repeat the exact same queries listed above.\n\n");
        }

        prompt.append(String.format("""
            [Constraints]
            - Generate 1 to %d sub-queries
            - Use 1 sub-query for focused, single-concept questions
            - Use 2-%d sub-queries for multi-aspect or comparative questions
            - Rewrite queries: expand abbreviations, resolve pronoun references, add domain context
            - Each sub-query must have a distinct intent — avoid redundant queries
            - Output strict JSON only, no markdown fences

            [Output Format]
            {
              "sub_queries": [
                {"rewritten_query": "optimized search query", "intent": "what this query aims to find"}
              ],
              "strategy": "HYBRID",
              "top_k": 10
            }
            """, maxSubQueries, maxSubQueries));

        return prompt.toString();
    }

    private RetrievalPlan parsePlanResponse(String response, String originalQuery, int maxSubQueries) {
        String json = extractJson(response);
        if (json == null) {
            log.warn("No JSON found in planner response, using fallback");
            return fallbackPlan(originalQuery);
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            List<SubQuery> subQueries = new ArrayList<>();
            JsonNode queries = root.get("sub_queries");
            if (queries != null && queries.isArray()) {
                for (JsonNode q : queries) {
                    subQueries.add(new SubQuery(
                        q.get("rewritten_query").asText(),
                        q.has("intent") ? q.get("intent").asText() : ""
                    ));
                }
            }

            if (subQueries.isEmpty()) {
                log.warn("Planner returned empty sub_queries, using fallback");
                return fallbackPlan(originalQuery);
            }

            // Enforce hard bound regardless of LLM output
            if (subQueries.size() > maxSubQueries) {
                subQueries = subQueries.subList(0, maxSubQueries);
            }

            String strategyStr = root.has("strategy") ? root.get("strategy").asText() : "HYBRID";
            RetrievalPlan.SearchStrategy strategy;
            try {
                strategy = RetrievalPlan.SearchStrategy.valueOf(strategyStr);
            } catch (IllegalArgumentException e) {
                strategy = RetrievalPlan.SearchStrategy.HYBRID;
            }
            int topK = root.has("top_k") ? root.get("top_k").asInt() : 10;

            return new RetrievalPlan(subQueries, strategy, topK);
        } catch (Exception e) {
            log.warn("Failed to parse planner response, using fallback: {}", e.getMessage());
            return fallbackPlan(originalQuery);
        }
    }

    /**
     * Three-step JSON extraction:
     * 1. Try direct parse
     * 2. Try regex extraction of first {...} block
     * 3. Return null (triggers fallback)
     */
    private String extractJson(String response) {
        if (response == null || response.isBlank()) return null;

        // Step 1: direct parse
        try {
            objectMapper.readTree(response);
            return response.trim();
        } catch (Exception ignored) {
        }

        // Step 2: regex extraction
        Matcher matcher = JSON_BLOCK.matcher(response);
        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    private RetrievalPlan fallbackPlan(String query) {
        return new RetrievalPlan(
            List.of(new SubQuery(query, "direct search")),
            RetrievalPlan.SearchStrategy.HYBRID,
            10
        );
    }
}
```

- [ ] **Step 4: Run all planner tests**

```bash
mvn test -pl rag-application -Dtest=LlmRetrievalPlannerTest -q
```
Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add rag-application/src/main/java/com/rag/application/agent/LlmRetrievalPlanner.java \
        rag-application/src/test/java/com/rag/application/agent/LlmRetrievalPlannerTest.java
git commit -m "feat: enhance LlmRetrievalPlanner with robust JSON parsing, history injection, bounded expansion, no-repeat constraint"
```

---

## Verification

- [ ] **Run full module test suite**

```bash
mvn test -pl rag-domain,rag-application -q
```
Expected: BUILD SUCCESS, no test failures.
