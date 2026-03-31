package com.rag.domain.identity.port;

import com.rag.domain.identity.model.AccessRule;
import com.rag.domain.identity.model.KnowledgeSpace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceRepository {
    KnowledgeSpace save(KnowledgeSpace space);
    Optional<KnowledgeSpace> findById(UUID spaceId);
    List<KnowledgeSpace> findAccessibleSpaces(String bu, String team, UUID userId);
    void saveAccessRules(UUID spaceId, List<AccessRule> rules);
    void deleteAccessRulesBySpaceId(UUID spaceId);
}
