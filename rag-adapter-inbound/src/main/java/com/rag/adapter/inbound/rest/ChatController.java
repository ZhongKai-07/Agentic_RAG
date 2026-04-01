package com.rag.adapter.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.adapter.inbound.dto.request.ChatRequest;
import com.rag.adapter.inbound.dto.request.CreateSessionRequest;
import com.rag.adapter.inbound.dto.response.MessageResponse;
import com.rag.adapter.inbound.dto.response.SessionResponse;
import com.rag.application.chat.ChatApplicationService;
import com.rag.application.identity.SpaceApplicationService;
import com.rag.domain.conversation.model.StreamEvent;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatApplicationService chatService;
    private final SpaceApplicationService spaceService;
    private final ObjectMapper objectMapper;
    private final Executor sseExecutor;

    public ChatController(ChatApplicationService chatService,
                           SpaceApplicationService spaceService,
                           ObjectMapper objectMapper,
                           @org.springframework.beans.factory.annotation.Qualifier("sseExecutor") Executor sseExecutor) {
        this.chatService = chatService;
        this.spaceService = spaceService;
        this.objectMapper = objectMapper;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping("/spaces/{spaceId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse createSession(@PathVariable UUID spaceId,
                                          @RequestHeader("X-User-Id") UUID userId,
                                          @RequestBody(required = false) CreateSessionRequest req) {
        spaceService.assertUserHasAccess(userId, spaceId);
        String title = req != null ? req.title() : null;
        return SessionResponse.from(chatService.createSession(userId, spaceId, title));
    }

    @GetMapping("/spaces/{spaceId}/sessions")
    public List<SessionResponse> listSessions(@PathVariable UUID spaceId,
                                               @RequestHeader("X-User-Id") UUID userId) {
        return chatService.listSessions(userId, spaceId).stream()
            .map(SessionResponse::from).toList();
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionDetailResponse getSession(@PathVariable UUID sessionId,
                                             @RequestHeader("X-User-Id") UUID userId) {
        var session = chatService.getSession(sessionId);
        chatService.assertSessionOwner(session, userId);
        return new SessionDetailResponse(
            SessionResponse.from(session),
            session.getMessages().stream().map(MessageResponse::from).toList()
        );
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable UUID sessionId,
                               @RequestHeader("X-User-Id") UUID userId) {
        var session = chatService.getSession(sessionId);
        chatService.assertSessionOwner(session, userId);
        chatService.deleteSession(sessionId);
    }

    /**
     * Chat endpoint with SSE streaming response.
     *
     * SSE event types:
     * - agent_thinking: {"round":1,"content":"Analyzing query..."}
     * - agent_searching: {"round":1,"queries":["query1","query2"]}
     * - agent_evaluating: {"round":1,"sufficient":false}
     * - content_delta: {"delta":"token text"}
     * - citation: {"index":1,"documentId":"...","title":"...","page":12,"snippet":"..."}
     * - done: {"messageId":"...","totalCitations":2}
     * - error: {"code":"AGENT_ERROR","message":"..."}
     */
    @PostMapping(value = "/sessions/{sessionId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable UUID sessionId,
                            @RequestHeader("X-User-Id") UUID userId,
                            @Valid @RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout

        sseExecutor.execute(() -> {
            try {
                var disposable = chatService.chat(sessionId, userId, req.message())
                    .doOnNext(event -> {
                        try {
                            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                                .name(toEventName(event))
                                .data(objectMapper.writeValueAsString(toEventData(event)));
                            emitter.send(builder);
                        } catch (Exception e) {
                            log.error("Failed to send SSE event: {}", e.getMessage());
                        }
                    })
                    .doOnComplete(emitter::complete)
                    .doOnError(e -> {
                        try {
                            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                                .name("error")
                                .data(objectMapper.writeValueAsString(
                                    StreamEvent.error("STREAM_ERROR", e.getMessage())));
                            emitter.send(builder);
                        } catch (Exception ignored) {}
                        emitter.completeWithError(e);
                    })
                    .subscribe();

                // Cancel the upstream Flux when the client disconnects or times out
                emitter.onCompletion(disposable::dispose);
                emitter.onTimeout(() -> {
                    disposable.dispose();
                    emitter.complete();
                });
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(objectMapper.writeValueAsString(
                            StreamEvent.error("CHAT_ERROR", e.getMessage()))));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Unwrap nested event types so the SSE data is flat JSON.
     * CitationEmit wraps a Citation record — serialize the Citation directly
     * to match the frontend's expected flat field structure.
     */
    private Object toEventData(StreamEvent event) {
        if (event instanceof StreamEvent.CitationEmit ce) {
            return ce.citation();
        }
        return event;
    }

    private String toEventName(StreamEvent event) {
        if (event instanceof StreamEvent.AgentThinking) return "agent_thinking";
        if (event instanceof StreamEvent.AgentSearching) return "agent_searching";
        if (event instanceof StreamEvent.AgentEvaluating) return "agent_evaluating";
        if (event instanceof StreamEvent.ContentDelta) return "content_delta";
        if (event instanceof StreamEvent.CitationEmit) return "citation";
        if (event instanceof StreamEvent.Done) return "done";
        if (event instanceof StreamEvent.Error) return "error";
        return "unknown";
    }

    record SessionDetailResponse(SessionResponse session, List<MessageResponse> messages) {}
}
