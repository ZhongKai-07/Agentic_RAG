package com.rag.adapter.inbound.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BatchDeleteRequest(@NotNull List<UUID> documentIds) {}
