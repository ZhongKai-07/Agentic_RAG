package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {
    List<MessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
