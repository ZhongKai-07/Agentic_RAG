package com.rag.domain.conversation.model;

import java.util.List;

public sealed interface StreamEvent {

    // Agent process events — frontend shows thinking/searching status
    record AgentThinking(int round, String content) implements StreamEvent {}
    record AgentSearching(int round, List<String> queries) implements StreamEvent {}
    record AgentEvaluating(int round, boolean sufficient) implements StreamEvent {}

    // Generation events — frontend streams typewriter rendering
    record ContentDelta(String delta) implements StreamEvent {}
    record CitationEmit(Citation citation) implements StreamEvent {}

    // Termination events
    record Done(String messageId, int totalCitations) implements StreamEvent {}
    record Error(String code, String message) implements StreamEvent {}

    // Factory methods
    static StreamEvent agentThinking(int round, String content) {
        return new AgentThinking(round, content);
    }
    static StreamEvent agentSearching(int round, List<String> queries) {
        return new AgentSearching(round, queries);
    }
    static StreamEvent agentEvaluating(int round, boolean sufficient) {
        return new AgentEvaluating(round, sufficient);
    }
    static StreamEvent contentDelta(String delta) {
        return new ContentDelta(delta);
    }
    static StreamEvent citationEmit(Citation citation) {
        return new CitationEmit(citation);
    }
    static StreamEvent done(String messageId, int totalCitations) {
        return new Done(messageId, totalCitations);
    }
    static StreamEvent error(String code, String message) {
        return new Error(code, message);
    }
}
