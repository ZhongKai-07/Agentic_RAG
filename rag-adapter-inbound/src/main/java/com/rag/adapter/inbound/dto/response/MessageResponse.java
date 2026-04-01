package com.rag.adapter.inbound.dto.response;

import com.rag.domain.conversation.model.Citation;
import com.rag.domain.conversation.model.Message;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
    UUID messageId, String role, String content,
    List<CitationResponse> citations, Integer tokenCount, Instant createdAt
) {
    public record CitationResponse(
        int citationIndex, UUID documentId, String documentTitle,
        String chunkId, Integer pageNumber, String sectionPath, String snippet
    ) {
        public static CitationResponse from(Citation c) {
            return new CitationResponse(
                c.citationIndex(), c.documentId(), c.documentTitle(),
                c.chunkId(), c.pageNumber(), c.sectionPath(), c.snippet()
            );
        }
    }

    public static MessageResponse from(Message m) {
        List<CitationResponse> citationResponses = m.getCitations() != null
            ? m.getCitations().stream().map(CitationResponse::from).toList()
            : List.of();
        return new MessageResponse(
            m.getMessageId(), m.getRole().name(), m.getContent(),
            citationResponses, m.getTokenCount(), m.getCreatedAt()
        );
    }
}
