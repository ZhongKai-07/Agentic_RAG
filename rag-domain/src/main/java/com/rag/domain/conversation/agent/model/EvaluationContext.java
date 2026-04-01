package com.rag.domain.conversation.agent.model;

import java.util.List;

public record EvaluationContext(
    String originalQuery,
    List<SubQuery> executedQueries,
    List<RetrievalResult> results,
    int currentRound,
    int maxRounds
) {}
