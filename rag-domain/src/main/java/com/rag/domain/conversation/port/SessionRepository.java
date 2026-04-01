package com.rag.domain.conversation.port;

import com.rag.domain.conversation.model.ChatSession;
import com.rag.domain.conversation.model.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {

    ChatSession save(ChatSession session);

    Optional<ChatSession> findById(UUID sessionId);

    /**
     * Find session by ID, eagerly loading messages (with citations).
     */
    Optional<ChatSession> findByIdWithMessages(UUID sessionId);

    List<ChatSession> findByUserIdAndSpaceId(UUID userId, UUID spaceId);

    void deleteById(UUID sessionId);

    Message saveMessage(UUID sessionId, Message message);
}
