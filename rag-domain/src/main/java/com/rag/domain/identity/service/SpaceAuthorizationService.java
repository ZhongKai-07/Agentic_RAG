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

    /**
     * Resolves the highest security clearance level a user has for a given space's access rules.
     * Returns MANAGEMENT if user has MANAGEMENT clearance, otherwise ALL.
     */
    public static SecurityLevel resolveUserClearance(List<AccessRule> rules, User user) {
        SecurityLevel highestClearance = SecurityLevel.ALL;
        for (AccessRule rule : rules) {
            boolean matches = switch (rule.targetType()) {
                case BU -> rule.targetValue().equals(user.getBu());
                case TEAM -> rule.targetValue().equals(user.getTeam());
                case USER -> rule.targetValue().equals(user.getUserId().toString());
            };
            if (matches && rule.docSecurityClearance() == SecurityLevel.MANAGEMENT) {
                highestClearance = SecurityLevel.MANAGEMENT;
            }
        }
        return highestClearance;
    }

    private boolean matchesRule(User user, AccessRule rule) {
        return switch (rule.targetType()) {
            case BU -> rule.targetValue().equals(user.getBu());
            case TEAM -> rule.targetValue().equals(user.getTeam());
            case USER -> rule.targetValue().equals(user.getUserId().toString());
        };
    }
}
