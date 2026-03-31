package com.rag.domain.identity.model;

public record RetrievalConfig(
    int maxAgentRounds,
    String chunkingStrategy,
    String metadataExtractionPrompt
) {
    public RetrievalConfig() {
        this(3, "semantic_header", "");
    }

    public int maxAgentRounds(int defaultValue) {
        return maxAgentRounds > 0 ? maxAgentRounds : defaultValue;
    }
}
