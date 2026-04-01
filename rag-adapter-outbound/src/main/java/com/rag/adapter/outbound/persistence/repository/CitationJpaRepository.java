package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.CitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CitationJpaRepository extends JpaRepository<CitationEntity, UUID> {
    List<CitationEntity> findByMessageIdOrderByCitationIndexAsc(UUID messageId);
    List<CitationEntity> findByMessageIdIn(List<UUID> messageIds);
}
