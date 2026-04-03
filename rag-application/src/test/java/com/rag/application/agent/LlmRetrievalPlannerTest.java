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
        RetrievalPlan plan = planner.plan(planContextWithMaxSubQueries("multi-aspect query", 2));
        assertThat(plan.subQueries()).hasSize(2);
    }

    @Test
    void plan_promptContainsPreviousAttemptsOnRound2() {
        LlmPort.LlmRequest[] captured = new LlmPort.LlmRequest[1];
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

    private PlanContext planContext(String query) {
        return new PlanContext(query, List.of(), new RetrievalConfig(), List.of());
    }

    private PlanContext planContextWithMaxSubQueries(String query, int max) {
        RetrievalConfig config = new RetrievalConfig(3, "semantic_header", "", max, false, 5, 0.02);
        return new PlanContext(query, List.of(), config, List.of());
    }
}
