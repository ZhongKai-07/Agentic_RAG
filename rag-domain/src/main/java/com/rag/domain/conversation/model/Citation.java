package com.rag.domain.conversation.model;

import java.util.UUID;

public record Citation(
    UUID citationId,
    int citationIndex,
    UUID documentId,
    String documentTitle,
    String chunkId,
    Integer pageNumber,
    String sectionPath,
    String snippet
) {}
