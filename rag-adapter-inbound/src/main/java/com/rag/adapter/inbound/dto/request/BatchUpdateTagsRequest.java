package com.rag.adapter.inbound.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BatchUpdateTagsRequest(
    @NotNull List<UUID> documentIds,
    List<String> tagsToAdd,
    List<String> tagsToRemove
) {}
