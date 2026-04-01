package com.rag.domain.knowledge.exception;

public class KnowledgeBaseEmptyException extends RuntimeException {

    private final String indexName;

    public KnowledgeBaseEmptyException(String indexName) {
        super("Knowledge base '" + indexName + "' does not exist. Upload documents to this space first.");
        this.indexName = indexName;
    }

    public String getIndexName() {
        return indexName;
    }
}
