package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.conversation.model.StreamEvent;
import com.rag.domain.identity.model.RetrievalConfig;
import com.rag.domain.knowledge.port.RerankPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> contentsCaptor = ArgumentCaptor.forClass(List.class);
        when(rerankPort.rerank(any(), contentsCaptor.capture(), anyInt()))
            .thenReturn(List.of(new RerankPort.RerankResult(0, 0.9)));
        when(generator.generateStream(any())).thenReturn(Flux.empty());

        AgentRequest request = new AgentRequest("query", List.of(),
            new RetrievalConfig(2, "semantic_header", "", 3, false, 5, 0.02),
            new SearchFilter("space-1", null, List.of()), "zh");
        orchestrator.orchestrate(request).blockLast();

        assertThat(contentsCaptor.getValue()).hasSize(1);
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
