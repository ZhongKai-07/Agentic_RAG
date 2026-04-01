package com.rag.domain.conversation.model;

import java.util.List;
import java.util.Map;

public record AgentTrace(
    int totalRounds,
    List<RoundTrace> rounds
) {
    public record RoundTrace(
        int round,
        List<String> subQueries,
        int resultsFound,
        boolean sufficient,
        String reasoning
    ) {}
}
