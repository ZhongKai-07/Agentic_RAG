package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    @Query("SELECT d FROM DocumentEntity d WHERE d.spaceId = :spaceId " +
           "AND (:search IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<DocumentEntity> findBySpaceId(@Param("spaceId") UUID spaceId,
                                        @Param("search") String search,
                                        Pageable pageable);

    long countBySpaceId(UUID spaceId);
    long countBySpaceIdAndStatus(UUID spaceId, String status);
}
