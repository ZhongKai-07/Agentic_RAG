package com.rag.adapter.inbound.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateAccessRulesRequest(
    @NotNull List<AccessRuleDto> rules
) {
    public record AccessRuleDto(
        @NotNull String targetType,
        @NotNull String targetValue,
        String docSecurityClearance
    ) {}
}
