package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.SpacePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SpacePermissionJpaRepository extends JpaRepository<SpacePermissionEntity, UUID> {
}
