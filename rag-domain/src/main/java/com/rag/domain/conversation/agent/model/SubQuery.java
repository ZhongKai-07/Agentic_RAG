package com.rag.domain.conversation.agent.model;

public record SubQuery(
    String rewrittenQuery,
    String intent
) {}
