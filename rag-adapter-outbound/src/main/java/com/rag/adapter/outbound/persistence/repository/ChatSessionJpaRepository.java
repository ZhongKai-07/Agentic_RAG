package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, UUID> {
    List<ChatSessionEntity> findByUserIdAndSpaceIdOrderByLastActiveAtDesc(UUID userId, UUID spaceId);
}
