package com.rag.domain.conversation.agent.model;

import com.rag.domain.identity.model.RetrievalConfig;

import java.util.List;

public record EvaluationContext(
    String originalQuery,
    List<SubQuery> executedQueries,
    List<RetrievalResult> results,
    int currentRound,
    int maxRounds,
    RetrievalConfig spaceConfig
) {}
