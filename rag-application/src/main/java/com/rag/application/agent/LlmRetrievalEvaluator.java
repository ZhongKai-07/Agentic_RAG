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

            return parseEvalResponse(response, context.originalQuery());
        } catch (Exception e) {
            log.warn("Evaluator LLM call failed on round {}/{}: {}",
                context.currentRound(), context.maxRounds(), e.getMessage());
            // On last retry round, proceed with what we have rather than looping forever
            if (context.currentRound() >= context.maxRounds() - 1) {
                return new EvaluationResult(true,
                    "Evaluation failed on final round, proceeding with current results",
                    List.of(), List.of());
            }
            // Otherwise, mark as insufficient to allow re-retrieval with the original query
            return new EvaluationResult(false, "Evaluation failed: " + e.getMessage(),
                List.of("evaluation error"), List.of(context.originalQuery()));
        }
    }

    private EvaluationResult parseEvalResponse(String response, String originalQuery) {
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
            log.warn("Failed to parse evaluator response, marking as insufficient: {}", e.getMessage());
            return new EvaluationResult(false, "Parse failed: " + e.getMessage(),
                List.of("parse error"), List.of(originalQuery));
        }
    }
}
