package com.rag.domain.conversation.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatSession {
    private UUID sessionId;
    private UUID userId;
    private UUID spaceId;
    private String title;
    private SessionStatus status;
    private List<Message> messages;
    private Instant createdAt;
    private Instant lastActiveAt;

    private static final int MAX_HISTORY_ROUNDS = 10;
    private static final int MAX_HISTORY_TOKENS = 4000;

    public ChatSession() {
        this.messages = new ArrayList<>();
        this.status = SessionStatus.ACTIVE;
    }

    public static ChatSession create(UUID userId, UUID spaceId, String title) {
        ChatSession session = new ChatSession();
        session.sessionId = UUID.randomUUID();
        session.userId = userId;
        session.spaceId = spaceId;
        session.title = title;
        session.createdAt = Instant.now();
        session.lastActiveAt = Instant.now();
        return session;
    }

    public void addMessage(Message message) {
        this.messages.add(message);
        this.lastActiveAt = Instant.now();
    }

    public void archive() {
        this.status = SessionStatus.ARCHIVED;
    }

    /**
     * Returns the most recent conversation history as LlmPort.ChatMessage pairs.
     * Applies two truncation strategies:
     * 1. Round-based: max MAX_HISTORY_ROUNDS rounds (20 messages)
     * 2. Token-based: cumulative tokens must not exceed MAX_HISTORY_TOKENS
     * Token truncation walks backwards from most recent, keeping only what fits.
     */
    public List<com.rag.domain.conversation.port.LlmPort.ChatMessage> getRecentHistory() {
        List<Message> allMessages = this.messages;
        // Step 1: round-based truncation
        int maxMessages = MAX_HISTORY_ROUNDS * 2;
        int start = Math.max(0, allMessages.size() - maxMessages);
        List<Message> candidates = allMessages.subList(start, allMessages.size());

        // Step 2: token-based truncation — walk backwards, keep what fits
        int tokenBudget = MAX_HISTORY_TOKENS;
        int tokenStart = candidates.size();
        for (int i = candidates.size() - 1; i >= 0; i--) {
            int tokens = candidates.get(i).getTokenCount() != null
                ? candidates.get(i).getTokenCount() : 0;
            if (tokenBudget - tokens < 0) break;
            tokenBudget -= tokens;
            tokenStart = i;
        }

        List<com.rag.domain.conversation.port.LlmPort.ChatMessage> history = new ArrayList<>();
        for (int i = tokenStart; i < candidates.size(); i++) {
            Message msg = candidates.get(i);
            history.add(new com.rag.domain.conversation.port.LlmPort.ChatMessage(
                msg.getRole().name().toLowerCase(), msg.getContent()));
        }
        return history;
    }

    /**
     * Auto-generates session title from first user message if not set.
     */
    public void autoTitle(String firstUserMessage) {
        if (this.title == null || this.title.isBlank()) {
            this.title = firstUserMessage.length() > 50
                ? firstUserMessage.substring(0, 50) + "..."
                : firstUserMessage;
        }
    }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
}
