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
                systemPrompt, context.history(), userMessage, 0.3));
            return parsePlanResponse(response, context.userQuery(), maxSubQueries);
        } catch (Exception e) {
            log.warn("Planner LLM call failed, using fallback: {}", e.getMessage());
            return fallbackPlan(context.userQuery());
        }
    }

    private String buildUserMessage(PlanContext context) {
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

        if (context.history() != null && !context.history().isEmpty()) {
            prompt.append("[Conversation History]\n");
            for (LlmPort.ChatMessage msg : context.history()) {
                String role = "user".equals(msg.role()) ? "User" : "Assistant";
                prompt.append(role).append(": ").append(msg.content()).append("\n");
            }
            prompt.append("\n");
        }

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
                        q.has("intent") ? q.get("intent").asText() : ""));
                }
            }

            if (subQueries.isEmpty()) {
                log.warn("Planner returned empty sub_queries, using fallback");
                return fallbackPlan(originalQuery);
            }

            if (subQueries.size() > maxSubQueries) {
                subQueries = subQueries.subList(0, maxSubQueries);
            }

            String strategyStr = root.has("strategy") ? root.get("strategy").asText() : "HYBRID";
            RetrievalPlan.SearchStrategy strategy;
            try { strategy = RetrievalPlan.SearchStrategy.valueOf(strategyStr); }
            catch (IllegalArgumentException e) { strategy = RetrievalPlan.SearchStrategy.HYBRID; }
            int topK = root.has("top_k") ? root.get("top_k").asInt() : 10;

            return new RetrievalPlan(subQueries, strategy, topK);
        } catch (Exception e) {
            log.warn("Failed to parse planner response, using fallback: {}", e.getMessage());
            return fallbackPlan(originalQuery);
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) return null;
        // Step 1: Try direct parse
        try {
            objectMapper.readTree(response);
            return response.trim();
        } catch (Exception ignored) {}
        // Step 2: Regex extract {...} block
        Matcher matcher = JSON_BLOCK.matcher(response);
        if (matcher.find()) return matcher.group();
        // Step 3: No JSON found
        return null;
    }

    private RetrievalPlan fallbackPlan(String query) {
        return new RetrievalPlan(
            List.of(new SubQuery(query, "direct search")),
            RetrievalPlan.SearchStrategy.HYBRID, 10);
    }
}
