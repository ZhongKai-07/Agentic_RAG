package com.rag.domain.conversation.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Message {
    private UUID messageId;
    private MessageRole role;
    private String content;
    private List<Citation> citations;
    private AgentTrace agentTrace;
    private Integer tokenCount;
    private Instant createdAt;

    public Message() {
        this.citations = new ArrayList<>();
    }

    public static Message userMessage(String content) {
        Message m = new Message();
        m.messageId = UUID.randomUUID();
        m.role = MessageRole.USER;
        m.content = content;
        m.tokenCount = estimateTokens(content);
        m.createdAt = Instant.now();
        return m;
    }

    public static Message assistantMessage(String content, List<Citation> citations,
                                            AgentTrace agentTrace) {
        Message m = new Message();
        m.messageId = UUID.randomUUID();
        m.role = MessageRole.ASSISTANT;
        m.content = content;
        m.citations = citations != null ? citations : new ArrayList<>();
        m.agentTrace = agentTrace;
        m.tokenCount = estimateTokens(content);
        m.createdAt = Instant.now();
        return m;
    }

    private static int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 3;
    }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }
    public MessageRole getRole() { return role; }
    public void setRole(MessageRole role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<Citation> getCitations() { return citations; }
    public void setCitations(List<Citation> citations) { this.citations = citations; }
    public AgentTrace getAgentTrace() { return agentTrace; }
    public void setAgentTrace(AgentTrace agentTrace) { this.agentTrace = agentTrace; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
