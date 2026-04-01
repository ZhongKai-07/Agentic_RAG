# Plan 4: Conversation & Agent Engine

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Conversation bounded context: multi-turn chat sessions with ReAct agent loop, intelligent hybrid retrieval (query rewrite → search → rerank → evaluate), streaming SSE responses, citation tracing, and session persistence. This is the core Q&A capability that connects users to the indexed knowledge base.

**Architecture:** ReAct Agent loop with 4 components: Planner (LLM query rewrite/split) → Executor (embed + hybrid search + rerank) → Evaluator (LLM sufficiency check) → Generator (LLM streaming answer with citations). Multi-turn context via sliding window (last 10 rounds). Sessions persisted to PostgreSQL, hot-cached in Redis.

**Tech Stack:** Spring AI (ChatClient streaming), SSE (SseEmitter), Spring Async, OpenSearch hybrid search, Reactor Flux, Redis (session cache)

**Depends on:** Plan 1 (infrastructure), Plan 2 (Identity domain, User, KnowledgeSpace, AccessRule), Plan 3 (Knowledge ports: EmbeddingPort, VectorStorePort, LlmPort, docparser pipeline)

**Existing assets (from Plan 1-3):**
- `LlmPort` with `streamChat(Flux<String>)` and `chat(String)` — in `rag-domain/conversation/port/`
- `EmbeddingPort`, `VectorStorePort`, `RerankPort` — in `rag-domain/knowledge/port/`
- `AliCloudLlmAdapter`, `AliCloudEmbeddingAdapter`, `LocalOpenSearchAdapter` — in `rag-adapter-outbound/`
- `SessionRepository` stub — in `rag-domain/conversation/port/`
- Database tables `t_chat_session`, `t_message`, `t_citation` — in Flyway V1
- `WebSocketConfig` (STOMP) — in `rag-adapter-inbound/websocket/`
- `ServiceRegistryConfig` with `RerankProperties` — in `rag-infrastructure/config/`
- `RetrievalConfig` record with `maxAgentRounds()` — in `rag-domain/identity/model/`

---

## File Structure

```
rag-domain/src/main/java/com/rag/domain/
├── conversation/
│   ├── model/
│   │   ├── ChatSession.java              (create)
│   │   ├── Message.java                  (create)
│   │   ├── Citation.java                 (create)
│   │   ├── AgentTrace.java               (create)
│   │   ├── SessionStatus.java            (create)
│   │   ├── MessageRole.java              (create)
│   │   └── StreamEvent.java              (create)
│   ├── agent/
│   │   ├── RetrievalPlanner.java         (create)
│   │   ├── RetrievalExecutor.java        (create)
│   │   ├── RetrievalEvaluator.java       (create)
│   │   ├── AnswerGenerator.java          (create)
│   │   ├── AgentOrchestrator.java        (create)
│   │   └── model/
│   │       ├── AgentRequest.java         (create)
│   │       ├── PlanContext.java           (create)
│   │       ├── RetrievalPlan.java        (create)
│   │       ├── SubQuery.java             (create)
│   │       ├── SearchFilter.java         (create)
│   │       ├── RetrievalResult.java      (create)
│   │       ├── EvaluationContext.java    (create)
│   │       ├── EvaluationResult.java     (create)
│   │       ├── GenerationContext.java    (create)
│   │       └── RetrievalFeedback.java    (create)
│   ├── service/
│   │   └── ChatService.java              (create)
│   └── port/
│       └── SessionRepository.java        (update - replace stub)

rag-application/src/main/java/com/rag/application/
├── chat/
│   └── ChatApplicationService.java       (create)
└── agent/
    ├── LlmRetrievalPlanner.java          (create)
    ├── HybridRetrievalExecutor.java      (create)
    ├── LlmRetrievalEvaluator.java        (create)
    └── LlmAnswerGenerator.java           (create)

rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/
├── rest/
│   └── ChatController.java              (create)
└── dto/
    ├── request/
    │   ├── CreateSessionRequest.java     (create)
    │   └── ChatRequest.java              (create)
    └── response/
        ├── SessionResponse.java          (create)
        └── MessageResponse.java          (create)

rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/
├── rerank/
│   └── AliCloudRerankAdapter.java        (create)
└── persistence/
    ├── entity/
    │   ├── ChatSessionEntity.java        (create)
    │   ├── MessageEntity.java            (create)
    │   └── CitationEntity.java           (create)
    ├── repository/
    │   ├── ChatSessionJpaRepository.java (create)
    │   ├── MessageJpaRepository.java     (create)
    │   └── CitationJpaRepository.java    (create)
    ├── mapper/
    │   └── ChatSessionMapper.java        (create)
    └── adapter/
        └── SessionRepositoryAdapter.java (create)
```

---

### Task 1: Conversation Domain Models

**Files:**
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/model/SessionStatus.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/model/MessageRole.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/model/Citation.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/model/AgentTrace.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/model/Message.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/model/ChatSession.java`

- [ ] **Step 1: Create SessionStatus enum**

`rag-domain/src/main/java/com/rag/domain/conversation/model/SessionStatus.java`:
```java
package com.rag.domain.conversation.model;

public enum SessionStatus {
    ACTIVE,
    ARCHIVED
}
```

- [ ] **Step 2: Create MessageRole enum**

`rag-domain/src/main/java/com/rag/domain/conversation/model/MessageRole.java`:
```java
package com.rag.domain.conversation.model;

public enum MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
```

- [ ] **Step 3: Create Citation record**

Maps to `t_citation` table. Represents a source reference linking an answer chunk back to an original document.

`rag-domain/src/main/java/com/rag/domain/conversation/model/Citation.java`:
```java
package com.rag.domain.conversation.model;

import java.util.UUID;

public record Citation(
    UUID citationId,
    int citationIndex,
    UUID documentId,
    String documentTitle,
    String chunkId,
    Integer pageNumber,
    String sectionPath,
    String snippet
) {}
```

- [ ] **Step 4: Create AgentTrace record**

Stores the agent's retrieval process for debugging/audit. Serialized as JSONB in `t_message.agent_trace`.

`rag-domain/src/main/java/com/rag/domain/conversation/model/AgentTrace.java`:
```java
package com.rag.domain.conversation.model;

import java.util.List;
import java.util.Map;

public record AgentTrace(
    int totalRounds,
    List<RoundTrace> rounds
) {
    public record RoundTrace(
        int round,
        List<String> subQueries,
        int resultsFound,
        boolean sufficient,
        String reasoning
    ) {}
}
```

- [ ] **Step 5: Create Message class**

`rag-domain/src/main/java/com/rag/domain/conversation/model/Message.java`:
```java
package com.rag.domain.conversation.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Message {
    private UUID messageId;
    private MessageRole role;
    private String content;
    private List<Citation> citations;
    private AgentTrace agentTrace;
    private Integer tokenCount;
    private Instant createdAt;

    public Message() {
        this.citations = new ArrayList<>();
    }

    public static Message userMessage(String content) {
        Message m = new Message();
        m.messageId = UUID.randomUUID();
        m.role = MessageRole.USER;
        m.content = content;
        m.tokenCount = estimateTokens(content);
        m.createdAt = Instant.now();
        return m;
    }

    public static Message assistantMessage(String content, List<Citation> citations,
                                            AgentTrace agentTrace) {
        Message m = new Message();
        m.messageId = UUID.randomUUID();
        m.role = MessageRole.ASSISTANT;
        m.content = content;
        m.citations = citations != null ? citations : new ArrayList<>();
        m.agentTrace = agentTrace;
        m.tokenCount = estimateTokens(content);
        m.createdAt = Instant.now();
        return m;
    }

    private static int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 3;
    }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }
    public MessageRole getRole() { return role; }
    public void setRole(MessageRole role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<Citation> getCitations() { return citations; }
    public void setCitations(List<Citation> citations) { this.citations = citations; }
    public AgentTrace getAgentTrace() { return agentTrace; }
    public void setAgentTrace(AgentTrace agentTrace) { this.agentTrace = agentTrace; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 6: Create ChatSession aggregate root**

`rag-domain/src/main/java/com/rag/domain/conversation/model/ChatSession.java`:
```java
package com.rag.domain.conversation.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatSession {
    private UUID sessionId;
    private UUID userId;
    private UUID spaceId;
    private String title;
    private SessionStatus status;
    private List<Message> messages;
    private Instant createdAt;
    private Instant lastActiveAt;

    private static final int MAX_HISTORY_ROUNDS = 10;
    private static final int MAX_HISTORY_TOKENS = 4000;

    public ChatSession() {
        this.messages = new ArrayList<>();
        this.status = SessionStatus.ACTIVE;
    }

    public static ChatSession create(UUID userId, UUID spaceId, String title) {
        ChatSession session = new ChatSession();
        session.sessionId = UUID.randomUUID();
        session.userId = userId;
        session.spaceId = spaceId;
        session.title = title;
        session.createdAt = Instant.now();
        session.lastActiveAt = Instant.now();
        return session;
    }

    public void addMessage(Message message) {
        this.messages.add(message);
        this.lastActiveAt = Instant.now();
    }

    public void archive() {
        this.status = SessionStatus.ARCHIVED;
    }

    /**
     * Returns the most recent conversation history as LlmPort.ChatMessage pairs.
     * Applies two truncation strategies:
     * 1. Round-based: max MAX_HISTORY_ROUNDS rounds (20 messages)
     * 2. Token-based: cumulative tokens must not exceed MAX_HISTORY_TOKENS
     * Token truncation walks backwards from most recent, keeping only what fits.
     */
    public List<com.rag.domain.conversation.port.LlmPort.ChatMessage> getRecentHistory() {
        List<Message> allMessages = this.messages;
        // Step 1: round-based truncation
        int maxMessages = MAX_HISTORY_ROUNDS * 2;
        int start = Math.max(0, allMessages.size() - maxMessages);
        List<Message> candidates = allMessages.subList(start, allMessages.size());

        // Step 2: token-based truncation — walk backwards, keep what fits
        int tokenBudget = MAX_HISTORY_TOKENS;
        int tokenStart = candidates.size();
        for (int i = candidates.size() - 1; i >= 0; i--) {
            int tokens = candidates.get(i).getTokenCount() != null
                ? candidates.get(i).getTokenCount() : 0;
            if (tokenBudget - tokens < 0) break;
            tokenBudget -= tokens;
            tokenStart = i;
        }

        List<com.rag.domain.conversation.port.LlmPort.ChatMessage> history = new ArrayList<>();
        for (int i = tokenStart; i < candidates.size(); i++) {
            Message msg = candidates.get(i);
            history.add(new com.rag.domain.conversation.port.LlmPort.ChatMessage(
                msg.getRole().name().toLowerCase(), msg.getContent()));
        }
        return history;
    }

    /**
     * Auto-generates session title from first user message if not set.
     */
    public void autoTitle(String firstUserMessage) {
        if (this.title == null || this.title.isBlank()) {
            this.title = firstUserMessage.length() > 50
                ? firstUserMessage.substring(0, 50) + "..."
                : firstUserMessage;
        }
    }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
}
```

- [ ] **Step 7: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-domain -q && echo "OK"`
Expected: OK

```bash
git add rag-domain/src/main/java/com/rag/domain/conversation/model/
git commit -m "feat(domain): add conversation models - ChatSession, Message, Citation, AgentTrace, enums"
```

---

### Task 2: StreamEvent (SSE Event Types)

**Files:**
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/model/StreamEvent.java`

- [ ] **Step 1: Create StreamEvent sealed interface**

Defines all SSE event types for the streaming chat response. Frontend uses these to render agent thinking indicators, streaming text, citations, and completion status.

`rag-domain/src/main/java/com/rag/domain/conversation/model/StreamEvent.java`:
```java
package com.rag.domain.conversation.model;

import java.util.List;

public sealed interface StreamEvent {

    // Agent process events — frontend shows thinking/searching status
    record AgentThinking(int round, String content) implements StreamEvent {}
    record AgentSearching(int round, List<String> queries) implements StreamEvent {}
    record AgentEvaluating(int round, boolean sufficient) implements StreamEvent {}

    // Generation events — frontend streams typewriter rendering
    record ContentDelta(String delta) implements StreamEvent {}
    record CitationEmit(Citation citation) implements StreamEvent {}

    // Termination events
    record Done(String messageId, int totalCitations) implements StreamEvent {}
    record Error(String code, String message) implements StreamEvent {}

    // Factory methods
    static StreamEvent agentThinking(int round, String content) {
        return new AgentThinking(round, content);
    }
    static StreamEvent agentSearching(int round, List<String> queries) {
        return new AgentSearching(round, queries);
    }
    static StreamEvent agentEvaluating(int round, boolean sufficient) {
        return new AgentEvaluating(round, sufficient);
    }
    static StreamEvent contentDelta(String delta) {
        return new ContentDelta(delta);
    }
    static StreamEvent citationEmit(Citation citation) {
        return new CitationEmit(citation);
    }
    static StreamEvent done(String messageId, int totalCitations) {
        return new Done(messageId, totalCitations);
    }
    static StreamEvent error(String code, String message) {
        return new Error(code, message);
    }
}
```

- [ ] **Step 2: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-domain -q && echo "OK"`
Expected: OK

```bash
git add rag-domain/src/main/java/com/rag/domain/conversation/model/StreamEvent.java
git commit -m "feat(domain): add StreamEvent sealed interface for SSE chat streaming"
```

---

### Task 3: Agent Abstraction Records + Interfaces

**Files:**
- Create: All files under `rag-domain/src/main/java/com/rag/domain/conversation/agent/model/`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/agent/RetrievalPlanner.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/agent/RetrievalExecutor.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/agent/RetrievalEvaluator.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/agent/AnswerGenerator.java`

- [ ] **Step 1: Create agent model records**

All records in `rag-domain/src/main/java/com/rag/domain/conversation/agent/model/`:

`SubQuery.java`:
```java
package com.rag.domain.conversation.agent.model;

public record SubQuery(
    String rewrittenQuery,
    String intent
) {}
```

`RetrievalPlan.java`:
```java
package com.rag.domain.conversation.agent.model;

import java.util.List;

public record RetrievalPlan(
    List<SubQuery> subQueries,
    SearchStrategy strategy,
    int topK
) {
    public enum SearchStrategy { VECTOR, KEYWORD, HYBRID }
}
```

`PlanContext.java`:
```java
package com.rag.domain.conversation.agent.model;

import com.rag.domain.conversation.port.LlmPort;
import com.rag.domain.identity.model.RetrievalConfig;

import java.util.List;

public record PlanContext(
    String userQuery,
    List<LlmPort.ChatMessage> history,
    RetrievalConfig spaceConfig,
    List<RetrievalFeedback> feedback
) {}
```

`SearchFilter.java`:
```java
package com.rag.domain.conversation.agent.model;

import com.rag.domain.shared.model.SecurityLevel;

import java.util.List;

public record SearchFilter(
    String indexName,
    SecurityLevel userClearance,
    List<String> accessibleTags
) {}
```

`RetrievalResult.java`:
```java
package com.rag.domain.conversation.agent.model;

import java.util.Map;

public record RetrievalResult(
    String chunkId,
    String documentId,
    String documentTitle,
    String content,
    int pageNumber,
    String sectionPath,
    double score,
    Map<String, String> highlights
) {}
```

`EvaluationContext.java`:
```java
package com.rag.domain.conversation.agent.model;

import java.util.List;

public record EvaluationContext(
    String originalQuery,
    List<SubQuery> executedQueries,
    List<RetrievalResult> results,
    int currentRound,
    int maxRounds
) {}
```

`EvaluationResult.java`:
```java
package com.rag.domain.conversation.agent.model;

import java.util.List;

public record EvaluationResult(
    boolean sufficient,
    String reasoning,
    List<String> missingAspects,
    List<String> suggestedNextQueries
) {}
```

`GenerationContext.java`:
```java
package com.rag.domain.conversation.agent.model;

import com.rag.domain.conversation.port.LlmPort;

import java.util.List;

public record GenerationContext(
    String userQuery,
    List<LlmPort.ChatMessage> history,
    List<RetrievalResult> allResults,
    String spaceLanguage
) {}
```

`RetrievalFeedback.java`:
```java
package com.rag.domain.conversation.agent.model;

import java.util.List;

public record RetrievalFeedback(
    int round,
    List<String> missingAspects,
    List<String> suggestedNextQueries
) {}
```

`AgentRequest.java`:
```java
package com.rag.domain.conversation.agent.model;

import com.rag.domain.conversation.port.LlmPort;
import com.rag.domain.identity.model.RetrievalConfig;

import java.util.List;

public record AgentRequest(
    String query,
    List<LlmPort.ChatMessage> history,
    RetrievalConfig spaceConfig,
    SearchFilter filter,
    String spaceLanguage
) {}
```

- [ ] **Step 2: Create agent component interfaces**

`rag-domain/src/main/java/com/rag/domain/conversation/agent/RetrievalPlanner.java`:
```java
package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.PlanContext;
import com.rag.domain.conversation.agent.model.RetrievalPlan;

public interface RetrievalPlanner {
    RetrievalPlan plan(PlanContext context);
}
```

`rag-domain/src/main/java/com/rag/domain/conversation/agent/RetrievalExecutor.java`:
```java
package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.RetrievalPlan;
import com.rag.domain.conversation.agent.model.RetrievalResult;
import com.rag.domain.conversation.agent.model.SearchFilter;

import java.util.List;

public interface RetrievalExecutor {
    List<RetrievalResult> execute(RetrievalPlan plan, SearchFilter filter);
}
```

`rag-domain/src/main/java/com/rag/domain/conversation/agent/RetrievalEvaluator.java`:
```java
package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.EvaluationContext;
import com.rag.domain.conversation.agent.model.EvaluationResult;

public interface RetrievalEvaluator {
    EvaluationResult evaluate(EvaluationContext context);
}
```

`rag-domain/src/main/java/com/rag/domain/conversation/agent/AnswerGenerator.java`:
```java
package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.GenerationContext;
import com.rag.domain.conversation.model.StreamEvent;
import reactor.core.publisher.Flux;

public interface AnswerGenerator {
    Flux<StreamEvent> generateStream(GenerationContext context);
}
```

- [ ] **Step 3: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-domain -q && echo "OK"`
Expected: OK

```bash
git add rag-domain/src/main/java/com/rag/domain/conversation/agent/
git commit -m "feat(domain): add agent abstractions - Planner/Executor/Evaluator/Generator interfaces and model records"
```

---

### Task 4: AgentOrchestrator + SessionRepository Port + ChatService

**Files:**
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/agent/AgentOrchestrator.java`
- Update: `rag-domain/src/main/java/com/rag/domain/conversation/port/SessionRepository.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/service/ChatService.java`

- [ ] **Step 1: Create AgentOrchestrator**

Core ReAct loop. Coordinates Planner → Executor → Evaluator in a loop, then delegates to Generator for streaming answer. Emits `StreamEvent` via Flux for SSE.

`rag-domain/src/main/java/com/rag/domain/conversation/agent/AgentOrchestrator.java`:
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
        return Flux.create(sink -> {
            try {
                int maxRounds = request.spaceConfig().maxAgentRounds(DEFAULT_MAX_ROUNDS);
                List<RetrievalResult> allResults = new ArrayList<>();
                List<RetrievalFeedback> feedbacks = new ArrayList<>();

                for (int round = 1; round <= maxRounds; round++) {
                    // 1. THINK — plan retrieval strategy
                    sink.next(StreamEvent.agentThinking(round, "Analyzing query..."));
                    PlanContext planCtx = new PlanContext(
                        request.query(), request.history(),
                        request.spaceConfig(), feedbacks);
                    RetrievalPlan plan = planner.plan(planCtx);

                    // 2. ACT — execute retrieval + rerank
                    List<String> queryTexts = plan.subQueries().stream()
                        .map(SubQuery::rewrittenQuery).toList();
                    sink.next(StreamEvent.agentSearching(round, queryTexts));

                    List<RetrievalResult> roundResults = executor.execute(plan, request.filter());

                    // Rerank if we have results
                    if (!roundResults.isEmpty()) {
                        roundResults = applyRerank(request.query(), roundResults);
                    }
                    allResults.addAll(roundResults);

                    // 3. EVALUATE — check if results are sufficient
                    EvaluationContext evalCtx = new EvaluationContext(
                        request.query(), plan.subQueries(),
                        allResults, round, maxRounds);
                    EvaluationResult eval = evaluator.evaluate(evalCtx);
                    sink.next(StreamEvent.agentEvaluating(round, eval.sufficient()));

                    if (eval.sufficient() || round == maxRounds) {
                        break;
                    }

                    // Not sufficient — add feedback for next round
                    feedbacks.add(new RetrievalFeedback(
                        round, eval.missingAspects(), eval.suggestedNextQueries()));
                }

                // 4. GENERATE — stream answer with citations
                List<RetrievalResult> deduped = deduplicateAndSort(allResults);
                GenerationContext genCtx = new GenerationContext(
                    request.query(), request.history(),
                    deduped, request.spaceLanguage());
                generator.generateStream(genCtx)
                    .doOnNext(sink::next)
                    .doOnComplete(sink::complete)
                    .doOnError(sink::error)
                    .subscribe();

            } catch (Exception e) {
                sink.next(StreamEvent.error("AGENT_ERROR", e.getMessage()));
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

    private List<RetrievalResult> deduplicateAndSort(List<RetrievalResult> results) {
        Map<String, RetrievalResult> seen = new LinkedHashMap<>();
        for (RetrievalResult r : results) {
            seen.putIfAbsent(r.chunkId(), r);
        }
        return new ArrayList<>(seen.values());
    }
}
```

- [ ] **Step 2: Update SessionRepository port**

Replace the stub with the full interface needed for session CRUD.

`rag-domain/src/main/java/com/rag/domain/conversation/port/SessionRepository.java`:
```java
package com.rag.domain.conversation.port;

import com.rag.domain.conversation.model.ChatSession;
import com.rag.domain.conversation.model.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {

    ChatSession save(ChatSession session);

    Optional<ChatSession> findById(UUID sessionId);

    /**
     * Find session by ID, eagerly loading messages (with citations).
     */
    Optional<ChatSession> findByIdWithMessages(UUID sessionId);

    List<ChatSession> findByUserIdAndSpaceId(UUID userId, UUID spaceId);

    void deleteById(UUID sessionId);

    Message saveMessage(UUID sessionId, Message message);
}
```

- [ ] **Step 3: Create ChatService**

Domain service for session lifecycle. Pure business logic, no framework deps.

`rag-domain/src/main/java/com/rag/domain/conversation/service/ChatService.java`:
```java
package com.rag.domain.conversation.service;

import com.rag.domain.conversation.model.ChatSession;
import com.rag.domain.conversation.model.Message;
import com.rag.domain.conversation.model.SessionStatus;

import java.util.UUID;

public class ChatService {

    /**
     * Creates a new chat session.
     */
    public ChatSession createSession(UUID userId, UUID spaceId, String title) {
        return ChatSession.create(userId, spaceId, title);
    }

    /**
     * Validates that a session is active and belongs to the user.
     */
    public void validateSessionForChat(ChatSession session, UUID userId) {
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalStateException("Session is archived and cannot accept new messages");
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Session does not belong to user");
        }
    }

    /**
     * Creates a user message and adds it to the session.
     */
    public Message addUserMessage(ChatSession session, String content) {
        Message userMsg = Message.userMessage(content);
        session.addMessage(userMsg);
        session.autoTitle(content);
        return userMsg;
    }

    /**
     * Creates an assistant message with citations and trace, and adds it to the session.
     */
    public Message addAssistantMessage(ChatSession session, String content,
                                        java.util.List<com.rag.domain.conversation.model.Citation> citations,
                                        com.rag.domain.conversation.model.AgentTrace agentTrace) {
        Message assistantMsg = Message.assistantMessage(content, citations, agentTrace);
        session.addMessage(assistantMsg);
        return assistantMsg;
    }
}
```

- [ ] **Step 4: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-domain -q && echo "OK"`
Expected: OK

```bash
git add rag-domain/src/main/java/com/rag/domain/conversation/agent/AgentOrchestrator.java \
        rag-domain/src/main/java/com/rag/domain/conversation/port/SessionRepository.java \
        rag-domain/src/main/java/com/rag/domain/conversation/service/ChatService.java
git commit -m "feat(domain): add AgentOrchestrator ReAct loop, SessionRepository port, ChatService"
```

---

### Task 5: JPA Entities + Spring Data Repositories (Outbound Persistence)

**Files:**
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/ChatSessionEntity.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/MessageEntity.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/CitationEntity.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/ChatSessionJpaRepository.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/MessageJpaRepository.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/CitationJpaRepository.java`

- [ ] **Step 1: Create ChatSessionEntity**

Maps to `t_chat_session` table (already exists via Flyway V1).

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/ChatSessionEntity.java`:
```java
package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_chat_session")
public class ChatSessionEntity {
    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(length = 256)
    private String title;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @PrePersist
    protected void onCreate() {
        if (sessionId == null) sessionId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (lastActiveAt == null) lastActiveAt = Instant.now();
    }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
}
```

- [ ] **Step 2: Create MessageEntity**

Maps to `t_message` table. `agent_trace` stored as JSONB string.

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/MessageEntity.java`:
```java
package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_message")
public class MessageEntity {
    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "agent_trace", columnDefinition = "JSONB")
    private String agentTrace;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (messageId == null) messageId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAgentTrace() { return agentTrace; }
    public void setAgentTrace(String agentTrace) { this.agentTrace = agentTrace; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: Create CitationEntity**

Maps to `t_citation` table.

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/CitationEntity.java`:
```java
package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "t_citation")
public class CitationEntity {
    @Id
    @Column(name = "citation_id")
    private UUID citationId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "citation_index", nullable = false)
    private int citationIndex;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_id", nullable = false, length = 128)
    private String chunkId;

    @Column(name = "document_title", nullable = false, length = 512)
    private String documentTitle;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "section_path", length = 512)
    private String sectionPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snippet;

    @PrePersist
    protected void onCreate() {
        if (citationId == null) citationId = UUID.randomUUID();
    }

    public UUID getCitationId() { return citationId; }
    public void setCitationId(UUID citationId) { this.citationId = citationId; }
    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }
    public int getCitationIndex() { return citationIndex; }
    public void setCitationIndex(int citationIndex) { this.citationIndex = citationIndex; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public String getSectionPath() { return sectionPath; }
    public void setSectionPath(String sectionPath) { this.sectionPath = sectionPath; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
}
```

- [ ] **Step 4: Create Spring Data JPA repositories**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/ChatSessionJpaRepository.java`:
```java
package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, UUID> {
    List<ChatSessionEntity> findByUserIdAndSpaceIdOrderByLastActiveAtDesc(UUID userId, UUID spaceId);
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/MessageJpaRepository.java`:
```java
package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {
    List<MessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/CitationJpaRepository.java`:
```java
package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.CitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CitationJpaRepository extends JpaRepository<CitationEntity, UUID> {
    List<CitationEntity> findByMessageIdOrderByCitationIndexAsc(UUID messageId);
    List<CitationEntity> findByMessageIdIn(List<UUID> messageIds);
}
```

- [ ] **Step 5: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-adapter-outbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/ChatSessionEntity.java \
        rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/MessageEntity.java \
        rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/CitationEntity.java \
        rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/ChatSessionJpaRepository.java \
        rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/MessageJpaRepository.java \
        rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/CitationJpaRepository.java
git commit -m "feat(adapter-outbound): add JPA entities and repos for chat session, message, citation"
```

---

### Task 6: ChatSessionMapper + SessionRepositoryAdapter

**Files:**
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/ChatSessionMapper.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/adapter/SessionRepositoryAdapter.java`

- [ ] **Step 1: Create ChatSessionMapper**

Converts between JPA entities and domain models. Follows the same pattern as `DocumentMapper`.

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/ChatSessionMapper.java`:
```java
package com.rag.adapter.outbound.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.adapter.outbound.persistence.entity.*;
import com.rag.domain.conversation.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ChatSessionMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static ChatSession toDomain(ChatSessionEntity e, List<MessageEntity> messageEntities,
                                        Map<UUID, List<CitationEntity>> citationsByMessageId) {
        ChatSession session = new ChatSession();
        session.setSessionId(e.getSessionId());
        session.setUserId(e.getUserId());
        session.setSpaceId(e.getSpaceId());
        session.setTitle(e.getTitle());
        session.setStatus(SessionStatus.valueOf(e.getStatus()));
        session.setCreatedAt(e.getCreatedAt());
        session.setLastActiveAt(e.getLastActiveAt());

        List<Message> messages = messageEntities.stream()
            .map(me -> toMessageDomain(me, citationsByMessageId.getOrDefault(me.getMessageId(), List.of())))
            .collect(Collectors.toCollection(ArrayList::new));
        session.setMessages(messages);
        return session;
    }

    public static ChatSession toDomainBasic(ChatSessionEntity e) {
        ChatSession session = new ChatSession();
        session.setSessionId(e.getSessionId());
        session.setUserId(e.getUserId());
        session.setSpaceId(e.getSpaceId());
        session.setTitle(e.getTitle());
        session.setStatus(SessionStatus.valueOf(e.getStatus()));
        session.setCreatedAt(e.getCreatedAt());
        session.setLastActiveAt(e.getLastActiveAt());
        return session;
    }

    public static ChatSessionEntity toEntity(ChatSession s) {
        ChatSessionEntity e = new ChatSessionEntity();
        e.setSessionId(s.getSessionId());
        e.setUserId(s.getUserId());
        e.setSpaceId(s.getSpaceId());
        e.setTitle(s.getTitle());
        e.setStatus(s.getStatus().name());
        e.setCreatedAt(s.getCreatedAt());
        e.setLastActiveAt(s.getLastActiveAt());
        return e;
    }

    public static Message toMessageDomain(MessageEntity e, List<CitationEntity> citationEntities) {
        Message m = new Message();
        m.setMessageId(e.getMessageId());
        m.setRole(MessageRole.valueOf(e.getRole()));
        m.setContent(e.getContent());
        m.setTokenCount(e.getTokenCount());
        m.setCreatedAt(e.getCreatedAt());

        if (e.getAgentTrace() != null && !e.getAgentTrace().isBlank()) {
            try {
                m.setAgentTrace(JSON.readValue(e.getAgentTrace(), AgentTrace.class));
            } catch (JsonProcessingException ignored) {}
        }

        List<Citation> citations = citationEntities.stream()
            .map(ChatSessionMapper::toCitationDomain).toList();
        m.setCitations(new ArrayList<>(citations));
        return m;
    }

    public static MessageEntity toMessageEntity(UUID sessionId, Message m) {
        MessageEntity e = new MessageEntity();
        e.setMessageId(m.getMessageId());
        e.setSessionId(sessionId);
        e.setRole(m.getRole().name());
        e.setContent(m.getContent());
        e.setTokenCount(m.getTokenCount());
        e.setCreatedAt(m.getCreatedAt());
        if (m.getAgentTrace() != null) {
            try {
                e.setAgentTrace(JSON.writeValueAsString(m.getAgentTrace()));
            } catch (JsonProcessingException ignored) {}
        }
        return e;
    }

    public static Citation toCitationDomain(CitationEntity e) {
        return new Citation(
            e.getCitationId(), e.getCitationIndex(),
            e.getDocumentId(), e.getDocumentTitle(),
            e.getChunkId(), e.getPageNumber(),
            e.getSectionPath(), e.getSnippet()
        );
    }

    public static CitationEntity toCitationEntity(UUID messageId, Citation c) {
        CitationEntity e = new CitationEntity();
        e.setCitationId(c.citationId() != null ? c.citationId() : UUID.randomUUID());
        e.setMessageId(messageId);
        e.setCitationIndex(c.citationIndex());
        e.setDocumentId(c.documentId());
        e.setDocumentTitle(c.documentTitle());
        e.setChunkId(c.chunkId());
        e.setPageNumber(c.pageNumber());
        e.setSectionPath(c.sectionPath());
        e.setSnippet(c.snippet());
        return e;
    }
}
```

- [ ] **Step 2: Create SessionRepositoryAdapter**

Implements `SessionRepository` domain port. Follows the adapter pattern like `DocumentRepositoryAdapter`.

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/adapter/SessionRepositoryAdapter.java`:
```java
package com.rag.adapter.outbound.persistence.adapter;

import com.rag.adapter.outbound.persistence.mapper.ChatSessionMapper;
import com.rag.adapter.outbound.persistence.repository.*;
import com.rag.domain.conversation.model.ChatSession;
import com.rag.domain.conversation.model.Message;
import com.rag.domain.conversation.port.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SessionRepositoryAdapter implements SessionRepository {

    private final ChatSessionJpaRepository sessionJpa;
    private final MessageJpaRepository messageJpa;
    private final CitationJpaRepository citationJpa;

    public SessionRepositoryAdapter(ChatSessionJpaRepository sessionJpa,
                                     MessageJpaRepository messageJpa,
                                     CitationJpaRepository citationJpa) {
        this.sessionJpa = sessionJpa;
        this.messageJpa = messageJpa;
        this.citationJpa = citationJpa;
    }

    @Override
    @Transactional
    public ChatSession save(ChatSession session) {
        var entity = ChatSessionMapper.toEntity(session);
        sessionJpa.save(entity);
        return session;
    }

    @Override
    public Optional<ChatSession> findById(UUID sessionId) {
        return sessionJpa.findById(sessionId)
            .map(ChatSessionMapper::toDomainBasic);
    }

    @Override
    public Optional<ChatSession> findByIdWithMessages(UUID sessionId) {
        return sessionJpa.findById(sessionId).map(entity -> {
            var messageEntities = messageJpa.findBySessionIdOrderByCreatedAtAsc(sessionId);
            var messageIds = messageEntities.stream()
                .map(me -> me.getMessageId()).toList();
            var allCitations = citationJpa.findByMessageIdIn(messageIds);
            var citationsByMessageId = allCitations.stream()
                .collect(Collectors.groupingBy(c -> c.getMessageId()));
            return ChatSessionMapper.toDomain(entity, messageEntities, citationsByMessageId);
        });
    }

    @Override
    public List<ChatSession> findByUserIdAndSpaceId(UUID userId, UUID spaceId) {
        return sessionJpa.findByUserIdAndSpaceIdOrderByLastActiveAtDesc(userId, spaceId)
            .stream()
            .map(ChatSessionMapper::toDomainBasic)
            .toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID sessionId) {
        sessionJpa.deleteById(sessionId);
    }

    @Override
    @Transactional
    public Message saveMessage(UUID sessionId, Message message) {
        var messageEntity = ChatSessionMapper.toMessageEntity(sessionId, message);
        messageJpa.save(messageEntity);

        if (message.getCitations() != null) {
            for (var citation : message.getCitations()) {
                var citationEntity = ChatSessionMapper.toCitationEntity(message.getMessageId(), citation);
                citationJpa.save(citationEntity);
            }
        }

        // Update session lastActiveAt
        sessionJpa.findById(sessionId).ifPresent(s -> {
            s.setLastActiveAt(java.time.Instant.now());
            sessionJpa.save(s);
        });

        return message;
    }
}
```

- [ ] **Step 3: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-adapter-outbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/ChatSessionMapper.java \
        rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/adapter/SessionRepositoryAdapter.java
git commit -m "feat(adapter-outbound): add ChatSessionMapper and SessionRepositoryAdapter"
```

---

### Task 7: AliCloudRerankAdapter

**Files:**
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/rerank/AliCloudRerankAdapter.java`

- [ ] **Step 1: Create AliCloud Rerank adapter**

Implements `RerankPort` using DashScope `gte-rerank` model via REST API. DashScope rerank endpoint is OpenAI-compatible at `/v1/rerank`.

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/rerank/AliCloudRerankAdapter.java`:
```java
package com.rag.adapter.outbound.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.domain.knowledge.port.RerankPort;
import com.rag.infrastructure.config.ServiceRegistryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Component
@Profile("local")
public class AliCloudRerankAdapter implements RerankPort {

    private static final Logger log = LoggerFactory.getLogger(AliCloudRerankAdapter.class);

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public AliCloudRerankAdapter(ServiceRegistryConfig.RerankProperties props,
                                  ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
            .baseUrl(props.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
        this.model = props.getModel();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("query", query);
            requestBody.put("documents", documents);
            requestBody.put("top_n", topN);

            String response = webClient.post()
                .uri("/v1/rerank")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");
            List<RerankResult> rerankResults = new ArrayList<>();
            if (results != null && results.isArray()) {
                for (JsonNode r : results) {
                    rerankResults.add(new RerankResult(
                        r.get("index").asInt(),
                        r.get("relevance_score").asDouble()
                    ));
                }
            }
            return rerankResults;
        } catch (Exception e) {
            log.warn("Rerank failed, returning original order: {}", e.getMessage());
            // Graceful degradation: return original order
            List<RerankResult> fallback = new ArrayList<>();
            for (int i = 0; i < Math.min(documents.size(), topN); i++) {
                fallback.add(new RerankResult(i, 1.0 - i * 0.01));
            }
            return fallback;
        }
    }
}
```

- [ ] **Step 2: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-adapter-outbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/rerank/AliCloudRerankAdapter.java
git commit -m "feat(adapter-outbound): add AliCloudRerankAdapter using DashScope gte-rerank"
```

---

### Task 8: Agent Component Implementations (Application Layer)

**Files:**
- Create: `rag-application/src/main/java/com/rag/application/agent/LlmRetrievalPlanner.java`
- Create: `rag-application/src/main/java/com/rag/application/agent/HybridRetrievalExecutor.java`
- Create: `rag-application/src/main/java/com/rag/application/agent/LlmRetrievalEvaluator.java`
- Create: `rag-application/src/main/java/com/rag/application/agent/LlmAnswerGenerator.java`

- [ ] **Step 1: Create LlmRetrievalPlanner**

Uses LLM to analyze user intent, rewrite queries, and plan retrieval strategy. Returns structured `RetrievalPlan` with sub-queries.

`rag-application/src/main/java/com/rag/application/agent/LlmRetrievalPlanner.java`:
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

@Component
public class LlmRetrievalPlanner implements RetrievalPlanner {

    private static final Logger log = LoggerFactory.getLogger(LlmRetrievalPlanner.class);

    private final LlmPort llmPort;
    private final ObjectMapper objectMapper;

    public LlmRetrievalPlanner(LlmPort llmPort, ObjectMapper objectMapper) {
        this.llmPort = llmPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public RetrievalPlan plan(PlanContext context) {
        String systemPrompt = buildPlannerPrompt(context);
        String userMessage = context.userQuery();

        // If there is feedback from previous rounds, include it
        if (context.feedback() != null && !context.feedback().isEmpty()) {
            RetrievalFeedback latest = context.feedback().get(context.feedback().size() - 1);
            userMessage = "Original query: " + context.userQuery()
                + "\n\nPrevious retrieval missed these aspects: " + latest.missingAspects()
                + "\nSuggested queries: " + latest.suggestedNextQueries();
        }

        try {
            String response = llmPort.chat(new LlmPort.LlmRequest(
                systemPrompt,
                context.history(),
                userMessage,
                0.3
            ));

            return parsePlanResponse(response);
        } catch (Exception e) {
            log.warn("Planner LLM call failed, using fallback: {}", e.getMessage());
            return fallbackPlan(context.userQuery());
        }
    }

    private String buildPlannerPrompt(PlanContext context) {
        return """
            You are a retrieval planner for a RAG system. Analyze the user's question and generate search queries.
            
            Respond in JSON format only:
            {
              "sub_queries": [
                {"rewritten_query": "optimized search query", "intent": "what this query aims to find"}
              ],
              "strategy": "HYBRID",
              "top_k": 10
            }
            
            Rules:
            - Rewrite queries for better retrieval (expand abbreviations, add context)
            - Split complex questions into 2-3 focused sub-queries
            - For simple questions, use 1 sub-query
            - Strategy should be HYBRID (keyword + vector) for most cases
            - Use KEYWORD for exact term lookups, VECTOR for semantic search
            """;
    }

    private RetrievalPlan parsePlanResponse(String response) {
        try {
            // Extract JSON from potential markdown code blocks
            String json = response;
            if (json.contains("```")) {
                json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
            }

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

            String strategyStr = root.has("strategy") ? root.get("strategy").asText() : "HYBRID";
            RetrievalPlan.SearchStrategy strategy;
            try {
                strategy = RetrievalPlan.SearchStrategy.valueOf(strategyStr);
            } catch (IllegalArgumentException e) {
                strategy = RetrievalPlan.SearchStrategy.HYBRID;
            }

            int topK = root.has("top_k") ? root.get("top_k").asInt() : 10;

            if (subQueries.isEmpty()) {
                return fallbackPlan(root.has("original_query")
                    ? root.get("original_query").asText() : "");
            }

            return new RetrievalPlan(subQueries, strategy, topK);
        } catch (Exception e) {
            log.warn("Failed to parse planner response: {}", e.getMessage());
            return fallbackPlan("");
        }
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

- [ ] **Step 2: Create HybridRetrievalExecutor**

Executes the retrieval plan: embeds each sub-query, runs hybrid search on OpenSearch, and merges results.

`rag-application/src/main/java/com/rag/application/agent/HybridRetrievalExecutor.java`:
```java
package com.rag.application.agent;

import com.rag.domain.conversation.agent.RetrievalExecutor;
import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.knowledge.port.EmbeddingPort;
import com.rag.domain.knowledge.port.VectorStorePort;
import com.rag.domain.shared.model.SecurityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class HybridRetrievalExecutor implements RetrievalExecutor {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalExecutor.class);

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;

    public HybridRetrievalExecutor(EmbeddingPort embeddingPort,
                                    VectorStorePort vectorStorePort) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
    }

    @Override
    public List<RetrievalResult> execute(RetrievalPlan plan, SearchFilter filter) {
        Map<String, RetrievalResult> mergedResults = new LinkedHashMap<>();

        for (SubQuery subQuery : plan.subQueries()) {
            try {
                // 1. Embed the query
                float[] queryVector = embeddingPort.embed(subQuery.rewrittenQuery());

                // 2. Build filters
                Map<String, Object> searchFilters = new HashMap<>();
                if (filter.userClearance() != null) {
                    // Include both ALL docs and user's clearance level
                    searchFilters.put("security_level", SecurityLevel.ALL.name());
                }

                // 3. Execute hybrid search
                var searchRequest = new VectorStorePort.HybridSearchRequest(
                    subQuery.rewrittenQuery(), queryVector,
                    searchFilters, plan.topK()
                );
                List<VectorStorePort.SearchHit> hits =
                    vectorStorePort.hybridSearch(filter.indexName(), searchRequest);

                // 4. Convert to RetrievalResult
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
            }
        }

        return new ArrayList<>(mergedResults.values());
    }

    private String getMetaString(Map<String, Object> meta, String key) {
        if (meta == null) return "";
        Object val = meta.get(key);
        return val != null ? val.toString() : "";
    }

    private int getMetaInt(Map<String, Object> meta, String key) {
        if (meta == null) return 0;
        Object val = meta.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (Exception e) { return 0; }
    }
}
```

- [ ] **Step 3: Create LlmRetrievalEvaluator**

Uses LLM to evaluate whether the retrieved results sufficiently answer the user's question.

`rag-application/src/main/java/com/rag/application/agent/LlmRetrievalEvaluator.java`:
```java
package com.rag.application.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.domain.conversation.agent.RetrievalEvaluator;
import com.rag.domain.conversation.agent.model.EvaluationContext;
import com.rag.domain.conversation.agent.model.EvaluationResult;
import com.rag.domain.conversation.port.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LlmRetrievalEvaluator implements RetrievalEvaluator {

    private static final Logger log = LoggerFactory.getLogger(LlmRetrievalEvaluator.class);

    private final LlmPort llmPort;
    private final ObjectMapper objectMapper;

    public LlmRetrievalEvaluator(LlmPort llmPort, ObjectMapper objectMapper) {
        this.llmPort = llmPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        // If this is the last round, skip evaluation and mark as sufficient
        if (context.currentRound() >= context.maxRounds()) {
            return new EvaluationResult(true, "Max rounds reached", List.of(), List.of());
        }

        // If no results at all, not sufficient
        if (context.results().isEmpty()) {
            return new EvaluationResult(false, "No results found",
                List.of("entire query"), List.of(context.originalQuery()));
        }

        try {
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

            String response = llmPort.chat(new LlmPort.LlmRequest(
                systemPrompt, List.of(), userMessage.toString(), 0.2));

            return parseEvalResponse(response);
        } catch (Exception e) {
            log.warn("Evaluator LLM call failed, marking as sufficient: {}", e.getMessage());
            return new EvaluationResult(true, "Evaluation failed, proceeding with current results",
                List.of(), List.of());
        }
    }

    private EvaluationResult parseEvalResponse(String response) {
        try {
            String json = response;
            if (json.contains("```")) {
                json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
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
            return new EvaluationResult(true, "Parse failed, proceeding", List.of(), List.of());
        }
    }
}
```

- [ ] **Step 4: Create LlmAnswerGenerator**

Streams the final answer via LLM, building citations from retrieval results. Emits `StreamEvent.ContentDelta`, `StreamEvent.CitationEmit`, and `StreamEvent.Done`.

`rag-application/src/main/java/com/rag/application/agent/LlmAnswerGenerator.java`:
```java
package com.rag.application.agent;

import com.rag.domain.conversation.agent.AnswerGenerator;
import com.rag.domain.conversation.agent.model.GenerationContext;
import com.rag.domain.conversation.agent.model.RetrievalResult;
import com.rag.domain.conversation.model.Citation;
import com.rag.domain.conversation.model.StreamEvent;
import com.rag.domain.conversation.port.LlmPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmAnswerGenerator implements AnswerGenerator {

    private final LlmPort llmPort;

    public LlmAnswerGenerator(LlmPort llmPort) {
        this.llmPort = llmPort;
    }

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
                // After streaming completes, emit citations and done
                List<Citation> citations = extractCitations(
                    fullContent.get().toString(), context.allResults());
                List<StreamEvent> events = new ArrayList<>();
                for (Citation c : citations) {
                    events.add(StreamEvent.citationEmit(c));
                }
                events.add(StreamEvent.done(messageId.toString(), citations.size()));
                return Flux.fromIterable(events);
            }));
    }

    private String buildGenerationPrompt(GenerationContext context) {
        String lang = "zh".equals(context.spaceLanguage()) ? "Chinese" : "English";
        return String.format("""
            You are a professional knowledge base Q&A assistant. Answer the user's question based on the provided reference materials.
            
            Rules:
            1. Answer in %s
            2. Only use information from the provided reference materials
            3. Cite sources using [1], [2], etc. matching the reference numbers
            4. If the references don't contain enough information, honestly state that
            5. Be precise and well-structured in your response
            6. For compliance/policy questions, quote the exact clause when possible
            """, lang);
    }

    private String buildUserMessage(GenerationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Reference materials:\n\n");
        List<RetrievalResult> results = context.allResults();
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            sb.append("[").append(i + 1).append("] ")
              .append(r.documentTitle());
            if (r.sectionPath() != null && !r.sectionPath().isEmpty()) {
                sb.append(" > ").append(r.sectionPath());
            }
            if (r.pageNumber() > 0) {
                sb.append(" (p.").append(r.pageNumber()).append(")");
            }
            sb.append("\n").append(r.content()).append("\n\n");
        }
        sb.append("---\nUser question: ").append(context.userQuery());
        return sb.toString();
    }

    private List<Citation> extractCitations(String content, List<RetrievalResult> results) {
        List<Citation> citations = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[(\\d+)]");
        Matcher matcher = pattern.matcher(content);
        java.util.Set<Integer> seen = new java.util.HashSet<>();

        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index >= 1 && index <= results.size() && seen.add(index)) {
                RetrievalResult r = results.get(index - 1);
                String snippet = r.content().length() > 200
                    ? r.content().substring(0, 200) + "..."
                    : r.content();
                citations.add(new Citation(
                    UUID.randomUUID(), index,
                    UUID.fromString(r.documentId()),
                    r.documentTitle(), r.chunkId(),
                    r.pageNumber() > 0 ? r.pageNumber() : null,
                    r.sectionPath(), snippet
                ));
            }
        }
        return citations;
    }
}
```

- [ ] **Step 5: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-application -q && echo "OK"`
Expected: OK

```bash
git add rag-application/src/main/java/com/rag/application/agent/
git commit -m "feat(application): add agent implementations - Planner, Executor, Evaluator, Generator"
```

---

### Task 9: ChatApplicationService

**Files:**
- Create: `rag-application/src/main/java/com/rag/application/chat/ChatApplicationService.java`

- [ ] **Step 1: Create ChatApplicationService**

Application service that orchestrates conversation flow: create/list/delete sessions, handle chat messages by wiring domain services (ChatService), agent components (AgentOrchestrator), and repositories (SessionRepository). Collects streamed results to persist the full assistant message after streaming completes.

`rag-application/src/main/java/com/rag/application/chat/ChatApplicationService.java`:
```java
package com.rag.application.chat;

import com.rag.domain.conversation.agent.AgentOrchestrator;
import com.rag.domain.conversation.agent.model.AgentRequest;
import com.rag.domain.conversation.agent.model.SearchFilter;
import com.rag.domain.conversation.model.*;
import com.rag.domain.conversation.port.SessionRepository;
import com.rag.domain.conversation.service.ChatService;
import com.rag.domain.identity.model.KnowledgeSpace;
import com.rag.domain.identity.model.User;
import com.rag.domain.identity.port.SpaceRepository;
import com.rag.domain.identity.port.UserRepository;
import com.rag.domain.identity.service.SpaceAuthorizationService;
import com.rag.domain.shared.model.SecurityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);

    private final SessionRepository sessionRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final ChatService chatService;

    public ChatApplicationService(SessionRepository sessionRepository,
                                   SpaceRepository spaceRepository,
                                   UserRepository userRepository,
                                   AgentOrchestrator agentOrchestrator) {
        this.sessionRepository = sessionRepository;
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
        this.agentOrchestrator = agentOrchestrator;
        this.chatService = new ChatService();
    }

    public ChatSession createSession(UUID userId, UUID spaceId, String title) {
        // Validate space exists
        spaceRepository.findById(spaceId)
            .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
        ChatSession session = chatService.createSession(userId, spaceId, title);
        return sessionRepository.save(session);
    }

    public List<ChatSession> listSessions(UUID userId, UUID spaceId) {
        return sessionRepository.findByUserIdAndSpaceId(userId, spaceId);
    }

    public ChatSession getSession(UUID sessionId) {
        return sessionRepository.findByIdWithMessages(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    public void deleteSession(UUID sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    /**
     * Handles a chat message: saves user message, runs agent, streams response,
     * then saves assistant message on completion.
     */
    public Flux<StreamEvent> chat(UUID sessionId, UUID userId, String userMessage) {
        // 1. Load session with history
        ChatSession session = sessionRepository.findByIdWithMessages(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        chatService.validateSessionForChat(session, userId);

        // 2. Save user message
        Message userMsg = chatService.addUserMessage(session, userMessage);
        sessionRepository.saveMessage(sessionId, userMsg);
        sessionRepository.save(session); // update title if auto-generated

        // 3. Load space config for agent
        KnowledgeSpace space = spaceRepository.findById(session.getSpaceId())
            .orElseThrow(() -> new IllegalArgumentException("Space not found"));

        // 4. Build search filter based on user permissions
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        SecurityLevel clearance = SpaceAuthorizationService.resolveUserClearance(
            space.getAccessRules(), user);
        SearchFilter filter = new SearchFilter(
            space.getIndexName(), clearance, List.of());

        // 5. Build agent request
        AgentRequest agentRequest = new AgentRequest(
            userMessage,
            session.getRecentHistory(),
            space.getRetrievalConfig(),
            filter,
            space.getLanguage()
        );

        // 6. Run agent and collect results for persistence
        List<Citation> collectedCitations = new ArrayList<>();
        StringBuilder collectedContent = new StringBuilder();
        List<AgentTrace.RoundTrace> roundTraces = new ArrayList<>();

        return agentOrchestrator.orchestrate(agentRequest)
            .doOnNext(event -> {
                // Collect data for persistence
                if (event instanceof StreamEvent.ContentDelta cd) {
                    collectedContent.append(cd.delta());
                } else if (event instanceof StreamEvent.CitationEmit ce) {
                    collectedCitations.add(ce.citation());
                } else if (event instanceof StreamEvent.AgentEvaluating ae) {
                    roundTraces.add(new AgentTrace.RoundTrace(
                        ae.round(), List.of(), 0, ae.sufficient(), ""));
                }
            })
            .doOnComplete(() -> {
                // Persist assistant message after streaming completes
                try {
                    AgentTrace trace = new AgentTrace(roundTraces.size(), roundTraces);
                    Message assistantMsg = chatService.addAssistantMessage(
                        session, collectedContent.toString(), collectedCitations, trace);
                    sessionRepository.saveMessage(sessionId, assistantMsg);
                    sessionRepository.save(session);
                } catch (Exception e) {
                    log.error("Failed to persist assistant message for session {}: {}",
                        sessionId, e.getMessage());
                }
            })
            .doOnError(e -> log.error("Chat stream error for session {}: {}",
                sessionId, e.getMessage()));
    }
}
```

- [ ] **Step 2: Add SpaceAuthorizationService.resolveUserClearance static method**

If not already present, add to `rag-domain/src/main/java/com/rag/domain/identity/service/SpaceAuthorizationService.java`:

```java
/**
 * Resolves the highest security clearance level a user has for a given space's access rules.
 * Returns MANAGEMENT if user has MANAGEMENT clearance, otherwise ALL.
 */
public static SecurityLevel resolveUserClearance(List<AccessRule> rules, User user) {
    SecurityLevel highestClearance = SecurityLevel.ALL;
    for (AccessRule rule : rules) {
        boolean matches = switch (rule.targetType()) {
            case BU -> rule.targetValue().equals(user.getBu());
            case TEAM -> rule.targetValue().equals(user.getTeam());
            case USER -> rule.targetValue().equals(user.getUserId().toString());
        };
        if (matches && rule.docSecurityClearance() == SecurityLevel.MANAGEMENT) {
            highestClearance = SecurityLevel.MANAGEMENT;
        }
    }
    return highestClearance;
}
```

- [ ] **Step 3: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-application -q && echo "OK"`
Expected: OK

```bash
git add rag-application/src/main/java/com/rag/application/chat/ \
        rag-domain/src/main/java/com/rag/domain/identity/service/SpaceAuthorizationService.java
git commit -m "feat(application): add ChatApplicationService with agent orchestration and session management"
```

---

### Task 10: AgentOrchestrator Spring Wiring

**Files:**
- Create: `rag-infrastructure/src/main/java/com/rag/infrastructure/config/AgentConfig.java`

- [ ] **Step 1: Create AgentConfig**

Wire the `AgentOrchestrator` as a Spring bean, injecting the agent component implementations and the RerankPort.

`rag-infrastructure/src/main/java/com/rag/infrastructure/config/AgentConfig.java`:
```java
package com.rag.infrastructure.config;

import com.rag.domain.conversation.agent.*;
import com.rag.domain.knowledge.port.RerankPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
```

- [ ] **Step 2: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-infrastructure -q && echo "OK"`
Expected: OK

```bash
git add rag-infrastructure/src/main/java/com/rag/infrastructure/config/AgentConfig.java
git commit -m "feat(infra): add AgentConfig to wire AgentOrchestrator bean"
```

---

### Task 11: Chat DTOs (Inbound)

**Files:**
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/CreateSessionRequest.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/ChatRequest.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/SessionResponse.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/MessageResponse.java`

- [ ] **Step 1: Create request DTOs**

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/CreateSessionRequest.java`:
```java
package com.rag.adapter.inbound.dto.request;

public record CreateSessionRequest(
    String title
) {}
```

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/ChatRequest.java`:
```java
package com.rag.adapter.inbound.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
    @NotBlank String message
) {}
```

- [ ] **Step 2: Create response DTOs**

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/SessionResponse.java`:
```java
package com.rag.adapter.inbound.dto.response;

import com.rag.domain.conversation.model.ChatSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionResponse(
    UUID sessionId, UUID userId, UUID spaceId, String title,
    String status, int messageCount, Instant createdAt, Instant lastActiveAt
) {
    public static SessionResponse from(ChatSession s) {
        return new SessionResponse(
            s.getSessionId(), s.getUserId(), s.getSpaceId(), s.getTitle(),
            s.getStatus().name(),
            s.getMessages() != null ? s.getMessages().size() : 0,
            s.getCreatedAt(), s.getLastActiveAt()
        );
    }
}
```

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/MessageResponse.java`:
```java
package com.rag.adapter.inbound.dto.response;

import com.rag.domain.conversation.model.Citation;
import com.rag.domain.conversation.model.Message;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
    UUID messageId, String role, String content,
    List<CitationResponse> citations, Integer tokenCount, Instant createdAt
) {
    public record CitationResponse(
        int citationIndex, UUID documentId, String documentTitle,
        String chunkId, Integer pageNumber, String sectionPath, String snippet
    ) {
        public static CitationResponse from(Citation c) {
            return new CitationResponse(
                c.citationIndex(), c.documentId(), c.documentTitle(),
                c.chunkId(), c.pageNumber(), c.sectionPath(), c.snippet()
            );
        }
    }

    public static MessageResponse from(Message m) {
        List<CitationResponse> citationResponses = m.getCitations() != null
            ? m.getCitations().stream().map(CitationResponse::from).toList()
            : List.of();
        return new MessageResponse(
            m.getMessageId(), m.getRole().name(), m.getContent(),
            citationResponses, m.getTokenCount(), m.getCreatedAt()
        );
    }
}
```

- [ ] **Step 3: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-adapter-inbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/CreateSessionRequest.java \
        rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/ChatRequest.java \
        rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/SessionResponse.java \
        rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/MessageResponse.java
git commit -m "feat(adapter-inbound): add chat DTOs - CreateSessionRequest, ChatRequest, SessionResponse, MessageResponse"
```

---

### Task 12: ChatController (SSE Streaming)

**Files:**
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/ChatController.java`

- [ ] **Step 1: Create ChatController**

REST controller with SSE streaming for chat. Follows existing controller patterns (SpaceController, DocumentController).

Session CRUD endpoints:
- `POST /api/v1/spaces/{spaceId}/sessions` — create session
- `GET /api/v1/spaces/{spaceId}/sessions` — list sessions
- `GET /api/v1/sessions/{sessionId}` — get session with messages
- `DELETE /api/v1/sessions/{sessionId}` — delete session

Chat endpoint (SSE):
- `POST /api/v1/sessions/{sessionId}/chat` — send message, returns SSE stream

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/ChatController.java`:
```java
package com.rag.adapter.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.adapter.inbound.dto.request.ChatRequest;
import com.rag.adapter.inbound.dto.request.CreateSessionRequest;
import com.rag.adapter.inbound.dto.response.MessageResponse;
import com.rag.adapter.inbound.dto.response.SessionResponse;
import com.rag.application.chat.ChatApplicationService;
import com.rag.domain.conversation.model.StreamEvent;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatApplicationService chatService;
    private final ObjectMapper objectMapper;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public ChatController(ChatApplicationService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/spaces/{spaceId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse createSession(@PathVariable UUID spaceId,
                                          @RequestHeader("X-User-Id") UUID userId,
                                          @RequestBody(required = false) CreateSessionRequest req) {
        String title = req != null ? req.title() : null;
        return SessionResponse.from(chatService.createSession(userId, spaceId, title));
    }

    @GetMapping("/spaces/{spaceId}/sessions")
    public List<SessionResponse> listSessions(@PathVariable UUID spaceId,
                                               @RequestHeader("X-User-Id") UUID userId) {
        return chatService.listSessions(userId, spaceId).stream()
            .map(SessionResponse::from).toList();
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionDetailResponse getSession(@PathVariable UUID sessionId) {
        var session = chatService.getSession(sessionId);
        return new SessionDetailResponse(
            SessionResponse.from(session),
            session.getMessages().stream().map(MessageResponse::from).toList()
        );
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable UUID sessionId) {
        chatService.deleteSession(sessionId);
    }

    /**
     * Chat endpoint with SSE streaming response.
     * 
     * SSE event types:
     * - agent_thinking: {"round":1,"content":"Analyzing query..."}
     * - agent_searching: {"round":1,"queries":["query1","query2"]}
     * - agent_evaluating: {"round":1,"sufficient":false}
     * - content_delta: {"delta":"token text"}
     * - citation: {"index":1,"documentId":"...","title":"...","page":12,"snippet":"..."}
     * - done: {"messageId":"...","totalCitations":2}
     * - error: {"code":"AGENT_ERROR","message":"..."}
     */
    @PostMapping(value = "/sessions/{sessionId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable UUID sessionId,
                            @RequestHeader("X-User-Id") UUID userId,
                            @Valid @RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout

        sseExecutor.execute(() -> {
            try {
                chatService.chat(sessionId, userId, req.message())
                    .doOnNext(event -> {
                        try {
                            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                                .name(toEventName(event))
                                .data(objectMapper.writeValueAsString(event));
                            emitter.send(builder);
                        } catch (Exception e) {
                            log.error("Failed to send SSE event: {}", e.getMessage());
                        }
                    })
                    .doOnComplete(emitter::complete)
                    .doOnError(e -> {
                        try {
                            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                                .name("error")
                                .data(objectMapper.writeValueAsString(
                                    StreamEvent.error("STREAM_ERROR", e.getMessage())));
                            emitter.send(builder);
                        } catch (Exception ignored) {}
                        emitter.completeWithError(e);
                    })
                    .subscribe();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(objectMapper.writeValueAsString(
                            StreamEvent.error("CHAT_ERROR", e.getMessage()))));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String toEventName(StreamEvent event) {
        return switch (event) {
            case StreamEvent.AgentThinking t -> "agent_thinking";
            case StreamEvent.AgentSearching s -> "agent_searching";
            case StreamEvent.AgentEvaluating e -> "agent_evaluating";
            case StreamEvent.ContentDelta d -> "content_delta";
            case StreamEvent.CitationEmit c -> "citation";
            case StreamEvent.Done d -> "done";
            case StreamEvent.Error e -> "error";
        };
    }

    record SessionDetailResponse(SessionResponse session, List<MessageResponse> messages) {}
}
```

- [ ] **Step 2: Verify and commit**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn compile -pl rag-adapter-inbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/ChatController.java
git commit -m "feat(adapter-inbound): add ChatController with SSE streaming chat endpoint"
```

---

### Task 13: Full Build Verification + CLAUDE.md Update

- [ ] **Step 1: Full compile**

Run: `cd "E:/AI Application/Agentic_RAG" && mvn clean compile && echo "OK"`
Expected: OK — all modules compile. If errors, fix and re-run.

- [ ] **Step 2: Update CLAUDE.md implementation status**

In `CLAUDE.md`, update:
```markdown
## Implementation Status

- [x] Plan 1: Project Foundation (modules, Docker, DB schema, SPI skeleton)
- [x] Plan 2: Identity & Document Management (domain models, JPA, REST APIs)
- [x] Plan 3: Document Processing Pipeline (async parsing, chunking, embedding, indexing)
- [x] Plan 4: Conversation & Agent Engine (ReAct agent, streaming, multi-turn, citations)
- [ ] Plan 5: React Frontend
```

Add to **Bounded Contexts** table:
```
| Conversation | ChatSession (with messages) | `com.rag.domain.conversation` |
```

Add to **Key Patterns**:
- **Agent ReAct Loop:** `AgentOrchestrator` in `rag-domain/conversation/agent/`. Coordinates Planner → Executor → Evaluator → Generator. Max rounds configured per space via `RetrievalConfig.maxAgentRounds`. Implementations in `rag-application/agent/`.
- **SSE Streaming:** Chat endpoint returns `SseEmitter` with typed events (`agent_thinking`, `content_delta`, `citation`, `done`, `error`). Controller in `ChatController`, serialized via Jackson.
- **Session Persistence:** `SessionRepositoryAdapter` saves session/messages/citations to PostgreSQL via JPA. `ChatApplicationService` collects streaming results in `doOnNext` and persists on `doOnComplete`.

Add to **API Convention**:
```
- Chat SSE: POST /api/v1/sessions/{id}/chat returns text/event-stream
- Session CRUD: /api/v1/spaces/{spaceId}/sessions (create, list), /api/v1/sessions/{id} (get, delete)
```

- [ ] **Step 3: Final commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md with Plan 4 status and patterns"
```

---

## Dependency Graph

```
Task 1 (Domain Models)
  └──► Task 2 (StreamEvent)
        └──► Task 3 (Agent Abstractions)
              └──► Task 4 (AgentOrchestrator + SessionRepo + ChatService)
                    ├──► Task 5 (JPA Entities + Repos)
                    │     └──► Task 6 (Mapper + SessionRepositoryAdapter)
                    ├──► Task 7 (RerankAdapter)
                    └──► Task 8 (Agent Implementations)
                          └──► Task 9 (ChatApplicationService)
                                └──► Task 10 (AgentConfig)
                                      └──► Task 11 (DTOs)
                                            └──► Task 12 (ChatController)
                                                  └──► Task 13 (Build + CLAUDE.md)
```

## Key Design Decisions

| Decision | Reason |
|----------|--------|
| Agent interfaces in domain, implementations in application | Domain stays framework-free; implementations need Spring DI for port injection |
| `AgentOrchestrator` in domain layer | Contains core ReAct business logic; takes interfaces as constructor params |
| AgentOrchestrator wired via `AgentConfig` bean | Domain class can't be @Component; infrastructure creates the bean |
| SSE via `SseEmitter` (not WebFlux) | Project uses spring-boot-starter-web (servlet), not WebFlux |
| Citations extracted post-stream | LLM streams tokens; citations parsed from complete text after stream ends |
| Rerank graceful degradation | If rerank API fails, returns original order instead of failing the query |
| `Flux.create` in AgentOrchestrator | Allows imperative ReAct loop (for/break) while emitting to reactive stream |
| `ChatApplicationService` collects in `doOnNext` | Need full content + citations for persistence after streaming completes |
