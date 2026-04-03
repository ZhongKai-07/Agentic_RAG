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

    @Test
    void priority1_maxRoundsReached_forcesSufficientWithoutLlmCall() {
        EvaluationContext ctx = context(List.of(), 3, 3, defaultConfig());
        EvaluationResult result = evaluator.evaluate(ctx);
        assertThat(result.sufficient()).isTrue();
        verifyNoInteractions(llmPort);
    }

    @Test
    void priority2_fastPath_triggersWhenEnabledAndThresholdMet() {
        RetrievalConfig fastPathConfig = new RetrievalConfig(3, "semantic_header", "", 3, true, 3, 0.02);
        List<RetrievalResult> results = List.of(
            resultWithScore("c1", 0.025),
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
        verify(llmPort, times(1)).chat(any());
    }

    @Test
    void priority2_fastPath_doesNotTriggerWhenScoreBelowThreshold() {
        RetrievalConfig fastPathConfig = new RetrievalConfig(3, "semantic_header", "", 3, true, 3, 0.02);
        List<RetrievalResult> results = List.of(
            resultWithScore("c1", 0.010),
            resultWithScore("c2", 0.008),
            resultWithScore("c3", 0.005)
        );
        when(llmPort.chat(any())).thenReturn(sufficientJson());
        EvaluationContext ctx = context(results, 1, 3, fastPathConfig);
        evaluator.evaluate(ctx);
        verify(llmPort, times(1)).chat(any());
    }

    @Test
    void priority3_emptyResults_returnsNotSufficient() {
        EvaluationContext ctx = context(List.of(), 1, 3, defaultConfig());
        EvaluationResult result = evaluator.evaluate(ctx);
        assertThat(result.sufficient()).isFalse();
        verifyNoInteractions(llmPort);
    }

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

    @Test
    void priority6_llmFailsWithFewResults_notSufficient() {
        when(llmPort.chat(any())).thenThrow(new RuntimeException("LLM timeout"));
        List<RetrievalResult> results = List.of(resultWithScore("c1", 0.01));
        EvaluationContext ctx = context(results, 1, 3, defaultConfig());
        EvaluationResult result = evaluator.evaluate(ctx);
        assertThat(result.sufficient()).isFalse();
    }

    // --- helpers ---
    private EvaluationContext context(List<RetrievalResult> results, int currentRound, int maxRounds, RetrievalConfig config) {
        return new EvaluationContext("test query", List.of(), results, currentRound, maxRounds, config);
    }

    private RetrievalConfig defaultConfig() {
        return new RetrievalConfig();
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
