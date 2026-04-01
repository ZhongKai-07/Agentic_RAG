package com.rag.adapter.outbound.persistence.adapter;

import com.rag.adapter.outbound.persistence.mapper.ChatSessionMapper;
import com.rag.adapter.outbound.persistence.repository.*;
import com.rag.domain.conversation.model.ChatSession;
import com.rag.domain.conversation.model.Message;
import com.rag.domain.conversation.port.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SessionRepositoryAdapter implements SessionRepository {

    private final ChatSessionJpaRepository sessionJpa;
    private final MessageJpaRepository messageJpa;
    private final CitationJpaRepository citationJpa;

    public SessionRepositoryAdapter(ChatSessionJpaRepository sessionJpa,
                                     MessageJpaRepository messageJpa,
                                     CitationJpaRepository citationJpa) {
        this.sessionJpa = sessionJpa;
        this.messageJpa = messageJpa;
        this.citationJpa = citationJpa;
    }

    @Override
    @Transactional
    public ChatSession save(ChatSession session) {
        var entity = ChatSessionMapper.toEntity(session);
        sessionJpa.save(entity);
        return session;
    }

    @Override
    public Optional<ChatSession> findById(UUID sessionId) {
        return sessionJpa.findById(sessionId)
            .map(ChatSessionMapper::toDomainBasic);
    }

    @Override
    public Optional<ChatSession> findByIdWithMessages(UUID sessionId) {
        return sessionJpa.findById(sessionId).map(entity -> {
            var messageEntities = messageJpa.findBySessionIdOrderByCreatedAtAsc(sessionId);
            var messageIds = messageEntities.stream()
                .map(me -> me.getMessageId()).toList();
            var allCitations = citationJpa.findByMessageIdIn(messageIds);
            var citationsByMessageId = allCitations.stream()
                .collect(Collectors.groupingBy(c -> c.getMessageId()));
            return ChatSessionMapper.toDomain(entity, messageEntities, citationsByMessageId);
        });
    }

    @Override
    public List<ChatSession> findByUserIdAndSpaceId(UUID userId, UUID spaceId) {
        return sessionJpa.findByUserIdAndSpaceIdOrderByLastActiveAtDesc(userId, spaceId)
            .stream()
            .map(ChatSessionMapper::toDomainBasic)
            .toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID sessionId) {
        sessionJpa.deleteById(sessionId);
    }

    @Override
    @Transactional
    public Message saveMessage(UUID sessionId, Message message) {
        var messageEntity = ChatSessionMapper.toMessageEntity(sessionId, message);
        messageJpa.save(messageEntity);

        if (message.getCitations() != null) {
            for (var citation : message.getCitations()) {
                var citationEntity = ChatSessionMapper.toCitationEntity(message.getMessageId(), citation);
                citationJpa.save(citationEntity);
            }
        }

        // Update session lastActiveAt
        sessionJpa.findById(sessionId).ifPresent(s -> {
            s.setLastActiveAt(java.time.Instant.now());
            sessionJpa.save(s);
        });

        return message;
    }
}
