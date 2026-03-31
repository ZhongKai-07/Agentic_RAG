package com.rag.domain.conversation.port;

import reactor.core.publisher.Flux;
import java.util.List;

public interface LlmPort {

    Flux<String> streamChat(LlmRequest request);

    String chat(LlmRequest request);

    record LlmRequest(
        String systemPrompt,
        List<ChatMessage> history,
        String userMessage,
        double temperature
    ) {}

    record ChatMessage(String role, String content) {}
}
