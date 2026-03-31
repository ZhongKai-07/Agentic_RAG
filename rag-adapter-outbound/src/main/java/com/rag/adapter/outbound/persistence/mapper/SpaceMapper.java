package com.rag.adapter.outbound.persistence.mapper;

import com.rag.adapter.outbound.persistence.entity.AccessRuleEntity;
import com.rag.adapter.outbound.persistence.entity.KnowledgeSpaceEntity;
import com.rag.domain.identity.model.*;
import com.rag.domain.shared.model.SecurityLevel;

import java.util.HashMap;
import java.util.Map;

public class SpaceMapper {

    public static KnowledgeSpace toDomain(KnowledgeSpaceEntity e) {
        KnowledgeSpace s = new KnowledgeSpace();
        s.setSpaceId(e.getSpaceId());
        s.setName(e.getName());
        s.setDescription(e.getDescription());
        s.setOwnerTeam(e.getOwnerTeam());
        s.setLanguage(e.getLanguage());
        s.setIndexName(e.getIndexName());
        s.setStatus(SpaceStatus.valueOf(e.getStatus()));
        s.setCreatedAt(e.getCreatedAt());
        s.setUpdatedAt(e.getUpdatedAt());

        Map<String, Object> rc = e.getRetrievalConfig();
        if (rc != null && !rc.isEmpty()) {
            s.setRetrievalConfig(new RetrievalConfig(
                rc.getOrDefault("maxAgentRounds", 3) instanceof Number n ? n.intValue() : 3,
                (String) rc.getOrDefault("chunkingStrategy", "semantic_header"),
                (String) rc.getOrDefault("metadataExtractionPrompt", "")
            ));
        } else {
            s.setRetrievalConfig(new RetrievalConfig());
        }
        return s;
    }

    public static KnowledgeSpaceEntity toEntity(KnowledgeSpace s) {
        KnowledgeSpaceEntity e = new KnowledgeSpaceEntity();
        e.setSpaceId(s.getSpaceId());
        e.setName(s.getName());
        e.setDescription(s.getDescription());
        e.setOwnerTeam(s.getOwnerTeam());
        e.setLanguage(s.getLanguage());
        e.setIndexName(s.getIndexName());
        e.setStatus(s.getStatus().name());
        e.setCreatedAt(s.getCreatedAt());
        e.setUpdatedAt(s.getUpdatedAt());

        RetrievalConfig rc = s.getRetrievalConfig();
        if (rc != null) {
            Map<String, Object> map = new HashMap<>();
            map.put("maxAgentRounds", rc.maxAgentRounds());
            map.put("chunkingStrategy", rc.chunkingStrategy());
            map.put("metadataExtractionPrompt", rc.metadataExtractionPrompt());
            e.setRetrievalConfig(map);
        } else {
            e.setRetrievalConfig(Map.of());
        }
        return e;
    }

    public static AccessRule toAccessRuleDomain(AccessRuleEntity e) {
        return new AccessRule(e.getRuleId(), e.getSpaceId(),
            TargetType.valueOf(e.getTargetType()), e.getTargetValue(),
            SecurityLevel.valueOf(e.getDocSecurityClearance()));
    }

    public static AccessRuleEntity toAccessRuleEntity(AccessRule r) {
        AccessRuleEntity e = new AccessRuleEntity();
        e.setRuleId(r.ruleId());
        e.setSpaceId(r.spaceId());
        e.setTargetType(r.targetType().name());
        e.setTargetValue(r.targetValue());
        e.setDocSecurityClearance(r.docSecurityClearance().name());
        return e;
    }
}
