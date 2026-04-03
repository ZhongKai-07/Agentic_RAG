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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

        List<StreamEvent> events = orchestrator.orchestrate(agentRequest())
            .collectList().block();

        assertThat(events).isNotNull();
        StreamEvent last = events.get(events.size() - 1);
        assertThat(last).isInstanceOf(StreamEvent.Error.class);
        assertThat(((StreamEvent.Error) last).code()).isEqualTo("KNOWLEDGE_BASE_EMPTY");
    }

    @Test
    void knowledgeBaseEmpty_withAccumulatedResults_proceedsToGenerate() {
        RetrievalResult existingResult = new RetrievalResult(
            "chunk-1", "doc-1", "Title", "content", 1, "sec", 0.02, Map.of());

        when(planner.plan(any())).thenReturn(simplePlan());
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
            RetrievalPlan.SearchStrategy.HYBRID, 10);
    }
}
