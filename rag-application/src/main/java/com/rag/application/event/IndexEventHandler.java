package com.rag.application.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.domain.conversation.port.LlmPort;
import com.rag.domain.document.event.ChunksIndexedEvent;
import com.rag.domain.document.event.DocumentParsedEvent;
import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentStatus;
import com.rag.domain.document.port.DocParserPort;
import com.rag.domain.document.port.DocumentRepository;
import com.rag.domain.identity.model.KnowledgeSpace;
import com.rag.domain.identity.port.SpaceRepository;
import com.rag.domain.knowledge.model.KnowledgeChunk;
import com.rag.domain.knowledge.port.EmbeddingPort;
import com.rag.domain.knowledge.port.VectorStorePort;
import com.rag.domain.knowledge.service.KnowledgeIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class IndexEventHandler {

    private static final Logger log = LoggerFactory.getLogger(IndexEventHandler.class);

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final LlmPort llmPort;
    private final DocumentRepository documentRepository;
    private final SpaceRepository spaceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final KnowledgeIndexService knowledgeIndexService;

    public IndexEventHandler(EmbeddingPort embeddingPort, VectorStorePort vectorStorePort,
                              LlmPort llmPort, DocumentRepository documentRepository,
                              SpaceRepository spaceRepository,
                              ApplicationEventPublisher eventPublisher,
                              ObjectMapper objectMapper) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.llmPort = llmPort;
        this.documentRepository = documentRepository;
        this.spaceRepository = spaceRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.knowledgeIndexService = new KnowledgeIndexService();
    }

    @Async("documentProcessingExecutor")
    @EventListener
    public void handle(DocumentParsedEvent event) {
        log.info("Indexing {} chunks for document {}", event.getChunks().size(), event.getDocumentId());

        Document document = documentRepository.findById(event.getDocumentId()).orElse(null);
        if (document == null) return;

        KnowledgeSpace space = spaceRepository.findById(event.getSpaceId()).orElse(null);
        if (space == null) return;

        try {
            // Transition: PARSED → INDEXING
            document.transitionTo(DocumentStatus.INDEXING);
            documentRepository.save(document);

            // Delete old chunks for this document (in case of re-index)
            vectorStorePort.deleteByDocumentId(space.getIndexName(), event.getDocumentId().toString());

            // 1. Build KnowledgeChunk list
            List<KnowledgeChunk> knowledgeChunks = new ArrayList<>();
            List<String> textsToEmbed = new ArrayList<>();

            String metadataPrompt = space.getRetrievalConfig() != null
                ? space.getRetrievalConfig().metadataExtractionPrompt() : "";

            for (int i = 0; i < event.getChunks().size(); i++) {
                DocParserPort.ParsedChunk chunk = event.getChunks().get(i);

                // LLM metadata extraction (if prompt configured)
                Map<String, Object> extractedTags = Map.of();
                if (metadataPrompt != null && !metadataPrompt.isEmpty()) {
                    extractedTags = extractMetadata(metadataPrompt, chunk.content());
                }

                // Document-level metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("security_level", document.getSecurityLevel().name());
                metadata.put("tags", document.getTags());
                metadata.put("file_type", document.getFileType().name());
                metadata.put("language", space.getLanguage());
                metadata.put("uploaded_by", document.getUploadedBy().toString());

                knowledgeChunks.add(new KnowledgeChunk(
                    UUID.randomUUID().toString(),
                    event.getDocumentId(), event.getVersionId(), event.getSpaceId(),
                    chunk.content(), i, chunk.pageNumber(), chunk.sectionPath(),
                    chunk.tokenCount(), event.getDocumentTitle(),
                    extractedTags, metadata
                ));
                textsToEmbed.add(chunk.content());
            }

            // 2. Batch embedding
            log.info("Generating embeddings for {} chunks", textsToEmbed.size());
            List<float[]> embeddings = embeddingPort.embedBatch(textsToEmbed);

            // 3. Build chunk documents and upsert
            var chunkDocs = knowledgeIndexService.buildChunkDocuments(knowledgeChunks, embeddings);
            vectorStorePort.upsertChunks(space.getIndexName(), chunkDocs);

            // Transition: INDEXING → INDEXED
            document.transitionTo(DocumentStatus.INDEXED);
            document.setChunkCount(knowledgeChunks.size());
            documentRepository.save(document);

            log.info("Successfully indexed {} chunks for document {}", knowledgeChunks.size(), event.getDocumentId());

            eventPublisher.publishEvent(new ChunksIndexedEvent(
                event.getDocumentId(), event.getSpaceId(), knowledgeChunks.size()));

        } catch (Exception e) {
            log.error("Failed to index document: {}", event.getDocumentId(), e);
            document.transitionTo(DocumentStatus.FAILED);
            documentRepository.save(document);
        }
    }

    private Map<String, Object> extractMetadata(String prompt, String chunkContent) {
        try {
            String systemPrompt = prompt + "\n\nReturn ONLY a JSON object with extracted tags. No explanation.";
            String response = llmPort.chat(new LlmPort.LlmRequest(
                systemPrompt, List.of(), chunkContent, 0.1));

            // Parse JSON from LLM response
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json?\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to extract metadata from chunk, skipping: {}", e.getMessage());
            return Map.of();
        }
    }
}
