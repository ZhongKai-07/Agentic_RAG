package com.rag.adapter.outbound.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.adapter.outbound.persistence.entity.*;
import com.rag.domain.conversation.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ChatSessionMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static ChatSession toDomain(ChatSessionEntity e, List<MessageEntity> messageEntities,
                                        Map<UUID, List<CitationEntity>> citationsByMessageId) {
        ChatSession session = new ChatSession();
        session.setSessionId(e.getSessionId());
        session.setUserId(e.getUserId());
        session.setSpaceId(e.getSpaceId());
        session.setTitle(e.getTitle());
        session.setStatus(SessionStatus.valueOf(e.getStatus()));
        session.setCreatedAt(e.getCreatedAt());
        session.setLastActiveAt(e.getLastActiveAt());

        List<Message> messages = messageEntities.stream()
            .map(me -> toMessageDomain(me, citationsByMessageId.getOrDefault(me.getMessageId(), List.of())))
            .collect(Collectors.toCollection(ArrayList::new));
        session.setMessages(messages);
        return session;
    }

    public static ChatSession toDomainBasic(ChatSessionEntity e) {
        ChatSession session = new ChatSession();
        session.setSessionId(e.getSessionId());
        session.setUserId(e.getUserId());
        session.setSpaceId(e.getSpaceId());
        session.setTitle(e.getTitle());
        session.setStatus(SessionStatus.valueOf(e.getStatus()));
        session.setCreatedAt(e.getCreatedAt());
        session.setLastActiveAt(e.getLastActiveAt());
        return session;
    }

    public static ChatSessionEntity toEntity(ChatSession s) {
        ChatSessionEntity e = new ChatSessionEntity();
        e.setSessionId(s.getSessionId());
        e.setUserId(s.getUserId());
        e.setSpaceId(s.getSpaceId());
        e.setTitle(s.getTitle());
        e.setStatus(s.getStatus().name());
        e.setCreatedAt(s.getCreatedAt());
        e.setLastActiveAt(s.getLastActiveAt());
        return e;
    }

    public static Message toMessageDomain(MessageEntity e, List<CitationEntity> citationEntities) {
        Message m = new Message();
        m.setMessageId(e.getMessageId());
        m.setRole(MessageRole.valueOf(e.getRole()));
        m.setContent(e.getContent());
        m.setTokenCount(e.getTokenCount());
        m.setCreatedAt(e.getCreatedAt());

        if (e.getAgentTrace() != null && !e.getAgentTrace().isBlank()) {
            try {
                m.setAgentTrace(JSON.readValue(e.getAgentTrace(), AgentTrace.class));
            } catch (JsonProcessingException ignored) {}
        }

        List<Citation> citations = citationEntities.stream()
            .map(ChatSessionMapper::toCitationDomain).toList();
        m.setCitations(new ArrayList<>(citations));
        return m;
    }

    public static MessageEntity toMessageEntity(UUID sessionId, Message m) {
        MessageEntity e = new MessageEntity();
        e.setMessageId(m.getMessageId());
        e.setSessionId(sessionId);
        e.setRole(m.getRole().name());
        e.setContent(m.getContent());
        e.setTokenCount(m.getTokenCount());
        e.setCreatedAt(m.getCreatedAt());
        if (m.getAgentTrace() != null) {
            try {
                e.setAgentTrace(JSON.writeValueAsString(m.getAgentTrace()));
            } catch (JsonProcessingException ignored) {}
        }
        return e;
    }

    public static Citation toCitationDomain(CitationEntity e) {
        return new Citation(
            e.getCitationId(), e.getCitationIndex(),
            e.getDocumentId(), e.getDocumentTitle(),
            e.getChunkId(), e.getPageNumber(),
            e.getSectionPath(), e.getSnippet()
        );
    }

    public static CitationEntity toCitationEntity(UUID messageId, Citation c) {
        CitationEntity e = new CitationEntity();
        e.setCitationId(c.citationId() != null ? c.citationId() : UUID.randomUUID());
        e.setMessageId(messageId);
        e.setCitationIndex(c.citationIndex());
        e.setDocumentId(c.documentId());
        e.setDocumentTitle(c.documentTitle());
        e.setChunkId(c.chunkId());
        e.setPageNumber(c.pageNumber());
        e.setSectionPath(c.sectionPath());
        e.setSnippet(c.snippet());
        return e;
    }
}
