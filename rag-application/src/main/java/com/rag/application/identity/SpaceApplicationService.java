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
                                       String language, String indexName, UUID creatorUserId) {
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
        KnowledgeSpace saved = spaceRepository.save(space);

        // Auto-create access rule for the creator so the space is visible in their list
        AccessRule creatorRule = new AccessRule(
            UUID.randomUUID(), saved.getSpaceId(),
            TargetType.USER, creatorUserId.toString(), SecurityLevel.MANAGEMENT);
        spaceRepository.saveAccessRules(saved.getSpaceId(), List.of(creatorRule));
        saved.setAccessRules(new java.util.ArrayList<>(List.of(creatorRule)));

        return saved;
    }

    public KnowledgeSpace getSpace(UUID spaceId) {
        return spaceRepository.findById(spaceId)
            .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
    }

    /**
     * Asserts the user has access to the given space. Throws SecurityException if not.
     */
    public void assertUserHasAccess(UUID userId, UUID spaceId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        List<KnowledgeSpace> accessible = spaceRepository.findAccessibleSpaces(
            user.getBu(), user.getTeam(), userId);
        boolean hasAccess = accessible.stream()
            .anyMatch(s -> s.getSpaceId().equals(spaceId));
        if (!hasAccess) {
            throw new SecurityException("User " + userId + " has no access to space " + spaceId);
        }
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
