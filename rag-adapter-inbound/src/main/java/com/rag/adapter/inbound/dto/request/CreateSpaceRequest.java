package com.rag.adapter.inbound.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateSpaceRequest(
    @NotBlank String name,
    String description,
    @NotBlank String ownerTeam,
    @NotBlank String language,
    @NotBlank String indexName
) {}
