package com.rag.domain.identity.model;

public record RetrievalConfig(
    int maxAgentRounds,
    String chunkingStrategy,
    String metadataExtractionPrompt,
    int maxSubQueries,
    boolean enableFastPath,
    int minSufficientChunks,
    double rawScoreThreshold
) {
    public RetrievalConfig() {
        this(3, "semantic_header", "", 3, false, 5, 0.02);
    }

    public int maxAgentRounds(int defaultValue) {
        return maxAgentRounds > 0 ? maxAgentRounds : defaultValue;
    }

    public int maxSubQueries(int defaultValue) {
        return maxSubQueries > 0 ? maxSubQueries : defaultValue;
    }
}
