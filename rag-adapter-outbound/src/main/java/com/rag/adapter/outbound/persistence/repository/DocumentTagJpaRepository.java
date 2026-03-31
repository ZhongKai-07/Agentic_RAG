package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.DocumentTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentTagJpaRepository extends JpaRepository<DocumentTagEntity, UUID> {
    List<DocumentTagEntity> findByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
    void deleteByDocumentIdAndTagNameIn(UUID documentId, List<String> tagNames);
}
