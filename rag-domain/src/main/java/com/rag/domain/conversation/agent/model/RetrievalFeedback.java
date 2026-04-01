package com.rag.domain.conversation.agent.model;

import java.util.List;

public record RetrievalFeedback(
    int round,
    List<String> missingAspects,
    List<String> suggestedNextQueries
) {}
