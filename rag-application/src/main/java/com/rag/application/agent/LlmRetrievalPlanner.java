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
