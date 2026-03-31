package com.rag.domain.knowledge.port;

import java.util.List;
import java.util.Map;

public interface VectorStorePort {

    void upsertChunks(String indexName, List<ChunkDocument> chunks);

    void deleteByDocumentId(String indexName, String documentId);

    List<SearchHit> hybridSearch(String indexName, HybridSearchRequest request);

    record ChunkDocument(
        String chunkId,
        String documentId,
        String content,
        float[] embedding,
        Map<String, Object> metadata
    ) {}

    record HybridSearchRequest(
        String query,
        float[] queryVector,
        Map<String, Object> filters,
        int topK
    ) {}

    record SearchHit(
        String chunkId,
        String documentId,
        String content,
        double score,
        Map<String, Object> metadata,
        Map<String, List<String>> highlights
    ) {}
}
