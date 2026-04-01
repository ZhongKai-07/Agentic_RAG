package com.rag.domain.conversation.agent.model;

import com.rag.domain.shared.model.SecurityLevel;

import java.util.List;

public record SearchFilter(
    String indexName,
    SecurityLevel userClearance,
    List<String> accessibleTags
) {}
