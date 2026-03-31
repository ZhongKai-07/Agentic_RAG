package com.rag.domain.knowledge.model;

import java.util.Map;
import java.util.UUID;

public record KnowledgeChunk(
    String chunkId,
    UUID documentId,
    UUID documentVersionId,
    UUID spaceId,
    String content,
    int chunkIndex,
    int pageNumber,
    String sectionPath,
    int tokenCount,
    String documentTitle,
    Map<String, Object> extractedTags,
    Map<String, Object> metadata
) {}
