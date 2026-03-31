package com.rag.domain.identity.service;

import com.rag.domain.identity.model.*;
import com.rag.domain.shared.model.SecurityLevel;

import java.util.List;

public class SpaceAuthorizationService {

    public boolean canAccessSpace(User user, KnowledgeSpace space) {
        return space.getAccessRules().stream().anyMatch(rule -> matchesRule(user, rule));
    }

    public SecurityLevel resolveSecurityClearance(User user, KnowledgeSpace space) {
        return space.getAccessRules().stream()
            .filter(rule -> matchesRule(user, rule))
            .map(AccessRule::docSecurityClearance)
            .reduce(SecurityLevel.ALL, (a, b) ->
                a == SecurityLevel.MANAGEMENT || b == SecurityLevel.MANAGEMENT
                    ? SecurityLevel.MANAGEMENT : SecurityLevel.ALL);
    }

    private boolean matchesRule(User user, AccessRule rule) {
        return switch (rule.targetType()) {
            case BU -> rule.targetValue().equals(user.getBu());
            case TEAM -> rule.targetValue().equals(user.getTeam());
            case USER -> rule.targetValue().equals(user.getUserId().toString());
        };
    }
}
