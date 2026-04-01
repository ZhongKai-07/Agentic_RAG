package com.rag.domain.document.model;

public enum DocumentStatus {
    UPLOADED,
    PARSING,
    PARSED,
    INDEXING,
    INDEXED,
    FAILED;

    public boolean canTransitionTo(DocumentStatus next) {
        return switch (this) {
            case UPLOADED -> next == PARSING || next == FAILED;
            case PARSING -> next == PARSED || next == FAILED || next == UPLOADED;
            case PARSED -> next == INDEXING || next == FAILED;
            case INDEXING -> next == INDEXED || next == FAILED;
            case INDEXED -> next == UPLOADED; // re-upload new version
            case FAILED -> next == UPLOADED || next == PARSING;  // retry
        };
    }
}
