package com.rag.domain.knowledge.service;

import com.rag.domain.knowledge.model.KnowledgeChunk;
import com.rag.domain.knowledge.port.VectorStorePort;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KnowledgeIndexService {

    public List<VectorStorePort.ChunkDocument> buildChunkDocuments(
            List<KnowledgeChunk> chunks, List<float[]> embeddings) {

        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                "Chunks and embeddings size mismatch: " + chunks.size() + " vs " + embeddings.size());
        }

        return java.util.stream.IntStream.range(0, chunks.size())
            .mapToObj(i -> {
                KnowledgeChunk chunk = chunks.get(i);
                float[] embedding = embeddings.get(i);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("document_id", chunk.documentId().toString());
                metadata.put("document_version_id", chunk.documentVersionId().toString());
                metadata.put("space_id", chunk.spaceId().toString());
                metadata.put("chunk_index", chunk.chunkIndex());
                metadata.put("page_number", chunk.pageNumber());
                metadata.put("section_path", chunk.sectionPath());
                metadata.put("document_title", chunk.documentTitle());
                metadata.put("indexed_at", Instant.now().toString());

                if (chunk.extractedTags() != null && !chunk.extractedTags().isEmpty()) {
                    metadata.put("extracted_tags", chunk.extractedTags());
                }
                if (chunk.metadata() != null) {
                    metadata.putAll(chunk.metadata());
                }

                return new VectorStorePort.ChunkDocument(
                    chunk.chunkId(), chunk.documentId().toString(),
                    chunk.content(), embedding, metadata
                );
            }).toList();
    }
}
