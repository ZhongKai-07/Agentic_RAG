package com.rag.domain.conversation.agent.model;

import java.util.List;

public record EvaluationResult(
    boolean sufficient,
    String reasoning,
    List<String> missingAspects,
    List<String> suggestedNextQueries
) {}
