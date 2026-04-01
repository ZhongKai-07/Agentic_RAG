package com.rag.adapter.inbound.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
    @NotBlank String message
) {}
