package com.rag.domain.conversation.agent.model;

import com.rag.domain.conversation.port.LlmPort;
import com.rag.domain.identity.model.RetrievalConfig;

import java.util.List;

public record PlanContext(
    String userQuery,
    List<LlmPort.ChatMessage> history,
    RetrievalConfig spaceConfig,
    List<RetrievalFeedback> feedback
) {}
