package com.rag.domain.identity.model;

import com.rag.domain.shared.model.SecurityLevel;
import java.util.UUID;

public record AccessRule(
    UUID ruleId,
    UUID spaceId,
    TargetType targetType,
    String targetValue,
    SecurityLevel docSecurityClearance
) {}
