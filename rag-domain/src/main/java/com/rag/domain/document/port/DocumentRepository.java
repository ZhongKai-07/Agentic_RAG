package com.rag.domain.document.port;

import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentVersion;
import com.rag.domain.shared.model.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(UUID documentId);
    PageResult<Document> findBySpaceId(UUID spaceId, int page, int size, String search);
    void deleteById(UUID documentId);
    void deleteByIds(List<UUID> documentIds);
    DocumentVersion saveVersion(DocumentVersion version);
    List<DocumentVersion> findVersionsByDocumentId(UUID documentId);
    void updateTags(UUID documentId, List<String> tags);
    void batchUpdateTags(List<UUID> documentIds, List<String> tagsToAdd, List<String> tagsToRemove);
    long countBySpaceId(UUID spaceId);
    long countBySpaceIdAndStatus(UUID spaceId, String status);
}
