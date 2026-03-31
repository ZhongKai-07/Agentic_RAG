package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.AccessRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AccessRuleJpaRepository extends JpaRepository<AccessRuleEntity, UUID> {
    List<AccessRuleEntity> findBySpaceId(UUID spaceId);
    void deleteBySpaceId(UUID spaceId);

    @Query("SELECT DISTINCT r.spaceId FROM AccessRuleEntity r WHERE " +
           "(r.targetType = 'BU' AND r.targetValue = :bu) OR " +
           "(r.targetType = 'TEAM' AND r.targetValue = :team) OR " +
           "(r.targetType = 'USER' AND r.targetValue = :userId)")
    List<UUID> findAccessibleSpaceIds(@Param("bu") String bu,
                                      @Param("team") String team,
                                      @Param("userId") String userId);
}
