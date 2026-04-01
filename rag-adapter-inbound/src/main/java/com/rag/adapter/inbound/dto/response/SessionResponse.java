package com.rag.adapter.inbound.dto.response;

import com.rag.domain.conversation.model.ChatSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionResponse(
    UUID sessionId, UUID userId, UUID spaceId, String title,
    String status, int messageCount, Instant createdAt, Instant lastActiveAt
) {
    public static SessionResponse from(ChatSession s) {
        return new SessionResponse(
            s.getSessionId(), s.getUserId(), s.getSpaceId(), s.getTitle(),
            s.getStatus().name(),
            s.getMessages() != null ? s.getMessages().size() : 0,
            s.getCreatedAt(), s.getLastActiveAt()
        );
    }
}
