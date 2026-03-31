package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.KnowledgeSpaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface KnowledgeSpaceJpaRepository extends JpaRepository<KnowledgeSpaceEntity, UUID> {
}
