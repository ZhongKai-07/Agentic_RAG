package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.DocumentVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentVersionJpaRepository extends JpaRepository<DocumentVersionEntity, UUID> {
    List<DocumentVersionEntity> findByDocumentIdOrderByVersionNoDesc(UUID documentId);
}
