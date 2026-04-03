package com.rag.application.agent;

import com.rag.domain.conversation.agent.AnswerGenerator;
import com.rag.domain.conversation.agent.model.GenerationContext;
import com.rag.domain.conversation.agent.model.RetrievalResult;
import com.rag.domain.conversation.model.Citation;
import com.rag.domain.conversation.model.StreamEvent;
import com.rag.domain.conversation.port.LlmPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmAnswerGenerator implements AnswerGenerator {

    private final LlmPort llmPort;

    public LlmAnswerGenerator(LlmPort llmPort) {
        this.llmPort = llmPort;
    }

    @Override
    public Flux<StreamEvent> generateStream(GenerationContext context) {
        String systemPrompt = buildGenerationPrompt(context);
        String userMessage = buildUserMessage(context);

        AtomicReference<StringBuilder> fullContent = new AtomicReference<>(new StringBuilder());
        UUID messageId = UUID.randomUUID();

        return llmPort.streamChat(new LlmPort.LlmRequest(
                systemPrompt, context.history(), userMessage, 0.7))
            .map(delta -> {
                fullContent.get().append(delta);
                return (StreamEvent) StreamEvent.contentDelta(delta);
            })
            .concatWith(Flux.defer(() -> {
                // After streaming completes, emit citations and done
                List<Citation> citations = extractCitations(
                    fullContent.get().toString(), context.allResults());
                List<StreamEvent> events = new ArrayList<>();
                for (Citation c : citations) {
                    events.add(StreamEvent.citationEmit(c));
                }
                events.add(StreamEvent.done(messageId.toString(), citations.size()));
                return Flux.fromIterable(events);
            }))
            .onErrorResume(e -> {
                // Guarantee SSE always terminates — never leaves frontend hanging
                return Flux.just(StreamEvent.error("GENERATOR_ERROR", e.getMessage()));
            });
    }

    private String buildGenerationPrompt(GenerationContext context) {
        String lang = "zh".equals(context.spaceLanguage()) ? "Chinese" : "English";
        return String.format("""
            You are a professional knowledge base Q&A assistant. Answer the user's question based on the provided reference materials.

            Rules:
            1. Answer in %s
            2. Only use information from the provided reference materials
            3. Cite sources using [1], [2], etc. matching the reference numbers
            4. If the references don't contain enough information, honestly state that
            5. Be precise and well-structured in your response
            6. For compliance/policy questions, quote the exact clause when possible
            """, lang);
    }

    private String buildUserMessage(GenerationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Reference materials:\n\n");
        List<RetrievalResult> results = context.allResults();
        // Limit to top 8 results to avoid exceeding LLM context window
        int limit = Math.min(results.size(), 8);
        for (int i = 0; i < limit; i++) {
            RetrievalResult r = results.get(i);
            sb.append("[").append(i + 1).append("] ")
              .append(r.documentTitle());
            if (r.sectionPath() != null && !r.sectionPath().isEmpty()) {
                sb.append(" > ").append(r.sectionPath());
            }
            if (r.pageNumber() > 0) {
                sb.append(" (p.").append(r.pageNumber()).append(")");
            }
            // Truncate long chunks to avoid exceeding LLM context window
            String content = r.content().length() > 1500
                ? r.content().substring(0, 1500) + "..."
                : r.content();
            sb.append("\n").append(content).append("\n\n");
        }
        sb.append("---\nUser question: ").append(context.userQuery());
        return sb.toString();
    }

    private List<Citation> extractCitations(String content, List<RetrievalResult> results) {
        List<Citation> citations = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[(\\d+)]");
        Matcher matcher = pattern.matcher(content);
        java.util.Set<Integer> seen = new java.util.HashSet<>();

        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index >= 1 && index <= results.size() && seen.add(index)) {
                RetrievalResult r = results.get(index - 1);
                String snippet = r.content().length() > 200
                    ? r.content().substring(0, 200) + "..."
                    : r.content();
                citations.add(new Citation(
                    UUID.randomUUID(), index,
                    UUID.fromString(r.documentId()),
                    r.documentTitle(), r.chunkId(),
                    r.pageNumber() > 0 ? r.pageNumber() : null,
                    r.sectionPath(), snippet
                ));
            }
        }
        return citations;
    }
}
