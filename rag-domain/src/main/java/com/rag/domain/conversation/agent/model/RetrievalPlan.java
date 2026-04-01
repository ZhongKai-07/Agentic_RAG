package com.rag.domain.conversation.agent.model;

import java.util.List;

public record RetrievalPlan(
    List<SubQuery> subQueries,
    SearchStrategy strategy,
    int topK
) {
    public enum SearchStrategy { VECTOR, KEYWORD, HYBRID }
}
