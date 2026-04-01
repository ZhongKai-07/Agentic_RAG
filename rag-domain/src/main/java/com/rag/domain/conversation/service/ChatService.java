package com.rag.domain.conversation.service;

import com.rag.domain.conversation.model.ChatSession;
import com.rag.domain.conversation.model.Message;
import com.rag.domain.conversation.model.SessionStatus;

import java.util.UUID;

public class ChatService {

    /**
     * Creates a new chat session.
     */
    public ChatSession createSession(UUID userId, UUID spaceId, String title) {
        return ChatSession.create(userId, spaceId, title);
    }

    /**
     * Validates that a session is active and belongs to the user.
     */
    public void validateSessionForChat(ChatSession session, UUID userId) {
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalStateException("Session is archived and cannot accept new messages");
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Session does not belong to user");
        }
    }

    /**
     * Creates a user message and adds it to the session.
     */
    public Message addUserMessage(ChatSession session, String content) {
        Message userMsg = Message.userMessage(content);
        session.addMessage(userMsg);
        session.autoTitle(content);
        return userMsg;
    }

    /**
     * Creates an assistant message with citations and trace, and adds it to the session.
     */
    public Message addAssistantMessage(ChatSession session, String content,
                                        java.util.List<com.rag.domain.conversation.model.Citation> citations,
                                        com.rag.domain.conversation.model.AgentTrace agentTrace) {
        Message assistantMsg = Message.assistantMessage(content, citations, agentTrace);
        session.addMessage(assistantMsg);
        return assistantMsg;
    }
}
