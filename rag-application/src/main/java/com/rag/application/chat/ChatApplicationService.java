package com.rag.application.chat;

import com.rag.domain.conversation.agent.AgentOrchestrator;
import com.rag.domain.conversation.agent.model.AgentRequest;
import com.rag.domain.conversation.agent.model.SearchFilter;
import com.rag.domain.conversation.model.*;
import com.rag.domain.conversation.port.SessionRepository;
import com.rag.domain.conversation.service.ChatService;
import com.rag.domain.identity.model.KnowledgeSpace;
import com.rag.domain.identity.model.User;
import com.rag.domain.identity.port.SpaceRepository;
import com.rag.domain.identity.port.UserRepository;
import com.rag.domain.identity.service.SpaceAuthorizationService;
import com.rag.domain.shared.model.SecurityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);

    private final SessionRepository sessionRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final ChatService chatService;

    public ChatApplicationService(SessionRepository sessionRepository,
                                   SpaceRepository spaceRepository,
                                   UserRepository userRepository,
                                   AgentOrchestrator agentOrchestrator) {
        this.sessionRepository = sessionRepository;
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
        this.agentOrchestrator = agentOrchestrator;
        this.chatService = new ChatService();
    }

    public ChatSession createSession(UUID userId, UUID spaceId, String title) {
        // Validate space exists
        spaceRepository.findById(spaceId)
            .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
        ChatSession session = chatService.createSession(userId, spaceId, title);
        return sessionRepository.save(session);
    }

    public List<ChatSession> listSessions(UUID userId, UUID spaceId) {
        return sessionRepository.findByUserIdAndSpaceId(userId, spaceId);
    }

    public ChatSession getSession(UUID sessionId) {
        return sessionRepository.findByIdWithMessages(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    public void deleteSession(UUID sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    /**
     * Handles a chat message: saves user message, runs agent, streams response,
     * then saves assistant message on completion.
     */
    public Flux<StreamEvent> chat(UUID sessionId, UUID userId, String userMessage) {
        // 1. Load session with history
        ChatSession session = sessionRepository.findByIdWithMessages(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        chatService.validateSessionForChat(session, userId);

        // 2. Save user message
        Message userMsg = chatService.addUserMessage(session, userMessage);
        sessionRepository.saveMessage(sessionId, userMsg);
        sessionRepository.save(session); // update title if auto-generated

        // 3. Load space config for agent
        KnowledgeSpace space = spaceRepository.findById(session.getSpaceId())
            .orElseThrow(() -> new IllegalArgumentException("Space not found"));

        // 4. Build search filter based on user permissions
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        SecurityLevel clearance = SpaceAuthorizationService.resolveUserClearance(
            space.getAccessRules(), user);
        SearchFilter filter = new SearchFilter(
            space.getIndexName(), clearance, List.of());

        // 5. Build agent request
        AgentRequest agentRequest = new AgentRequest(
            userMessage,
            session.getRecentHistory(),
            space.getRetrievalConfig(),
            filter,
            space.getLanguage()
        );

        // 6. Run agent and collect results for persistence
        List<Citation> collectedCitations = new ArrayList<>();
        StringBuilder collectedContent = new StringBuilder();
        List<AgentTrace.RoundTrace> roundTraces = new ArrayList<>();

        return agentOrchestrator.orchestrate(agentRequest)
            .doOnNext(event -> {
                // Collect data for persistence
                if (event instanceof StreamEvent.ContentDelta cd) {
                    collectedContent.append(cd.delta());
                } else if (event instanceof StreamEvent.CitationEmit ce) {
                    collectedCitations.add(ce.citation());
                } else if (event instanceof StreamEvent.AgentEvaluating ae) {
                    roundTraces.add(new AgentTrace.RoundTrace(
                        ae.round(), List.of(), 0, ae.sufficient(), ""));
                }
            })
            .doOnComplete(() -> {
                // Persist assistant message after streaming completes
                try {
                    AgentTrace trace = new AgentTrace(roundTraces.size(), roundTraces);
                    Message assistantMsg = chatService.addAssistantMessage(
                        session, collectedContent.toString(), collectedCitations, trace);
                    sessionRepository.saveMessage(sessionId, assistantMsg);
                    sessionRepository.save(session);
                } catch (Exception e) {
                    log.error("Failed to persist assistant message for session {}: {}",
                        sessionId, e.getMessage());
                }
            })
            .doOnError(e -> log.error("Chat stream error for session {}: {}",
                sessionId, e.getMessage()));
    }
}
