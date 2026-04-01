package com.rag.domain.conversation.agent.model;

import com.rag.domain.conversation.port.LlmPort;

import java.util.List;

public record GenerationContext(
    String userQuery,
    List<LlmPort.ChatMessage> history,
    List<RetrievalResult> allResults,
    String spaceLanguage
) {}
