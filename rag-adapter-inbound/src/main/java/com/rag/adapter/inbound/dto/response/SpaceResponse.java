package com.rag.adapter.inbound.dto.response;

import com.rag.domain.identity.model.KnowledgeSpace;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpaceResponse(
    UUID spaceId, String name, String description, String ownerTeam,
    String language, String indexName, String status,
    List<AccessRuleResponse> accessRules, Instant createdAt, Instant updatedAt
) {
    public record AccessRuleResponse(UUID ruleId, String targetType, String targetValue, String docSecurityClearance) {}

    public static SpaceResponse from(KnowledgeSpace s) {
        var rules = s.getAccessRules().stream().map(r ->
            new AccessRuleResponse(r.ruleId(), r.targetType().name(),
                r.targetValue(), r.docSecurityClearance().name())
        ).toList();
        return new SpaceResponse(s.getSpaceId(), s.getName(), s.getDescription(),
            s.getOwnerTeam(), s.getLanguage(), s.getIndexName(), s.getStatus().name(),
            rules, s.getCreatedAt(), s.getUpdatedAt());
    }
}
