package com.rag.domain.conversation.agent.model;

import java.util.Map;

public record RetrievalResult(
    String chunkId,
    String documentId,
    String documentTitle,
    String content,
    int pageNumber,
    String sectionPath,
    double score,
    Map<String, String> highlights
) {}
