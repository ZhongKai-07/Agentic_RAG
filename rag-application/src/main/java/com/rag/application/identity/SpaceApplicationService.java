package com.rag.application.identity;

import com.rag.domain.identity.model.*;
import com.rag.domain.identity.port.SpaceRepository;
import com.rag.domain.identity.port.UserRepository;
import com.rag.domain.shared.model.SecurityLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SpaceApplicationService {

    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;

    public SpaceApplicationService(SpaceRepository spaceRepository, UserRepository userRepository) {
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public KnowledgeSpace createSpace(String name, String description, String ownerTeam,
                                       String language, String indexName) {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(UUID.randomUUID());
        space.setName(name);
        space.setDescription(description);
        space.setOwnerTeam(ownerTeam);
        space.setLanguage(language);
        space.setIndexName(indexName);
        space.setRetrievalConfig(new RetrievalConfig());
        space.setStatus(SpaceStatus.ACTIVE);
        space.setCreatedAt(Instant.now());
        space.setUpdatedAt(Instant.now());
        return spaceRepository.save(space);
    }

    public KnowledgeSpace getSpace(UUID spaceId) {
        return spaceRepository.findById(spaceId)
            .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
    }

    public List<KnowledgeSpace> listAccessibleSpaces(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return spaceRepository.findAccessibleSpaces(user.getBu(), user.getTeam(), userId);
    }

    @Transactional
    public KnowledgeSpace updateAccessRules(UUID spaceId, List<AccessRule> newRules) {
        KnowledgeSpace space = getSpace(spaceId);
        spaceRepository.deleteAccessRulesBySpaceId(spaceId);
        List<AccessRule> rulesWithIds = newRules.stream()
            .map(r -> new AccessRule(UUID.randomUUID(), spaceId,
                r.targetType(), r.targetValue(), r.docSecurityClearance()))
            .toList();
        spaceRepository.saveAccessRules(spaceId, rulesWithIds);
        return spaceRepository.findById(spaceId).orElseThrow();
    }
}
