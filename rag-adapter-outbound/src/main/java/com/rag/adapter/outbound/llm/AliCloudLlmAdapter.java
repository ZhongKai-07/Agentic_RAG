package com.rag.adapter.outbound.llm;

import com.rag.domain.conversation.port.LlmPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local")
public class AliCloudLlmAdapter implements LlmPort {

    private final ChatClient chatClient;

    public AliCloudLlmAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Flux<String> streamChat(LlmRequest request) {
        return chatClient.prompt()
            .system(request.systemPrompt())
            .messages(toSpringMessages(request.history()))
            .user(request.userMessage())
            .stream()
            .content();
    }

    @Override
    public String chat(LlmRequest request) {
        return chatClient.prompt()
            .system(request.systemPrompt())
            .messages(toSpringMessages(request.history()))
            .user(request.userMessage())
            .call()
            .content();
    }

    private List<Message> toSpringMessages(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) return List.of();
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            switch (msg.role()) {
                case "user" -> messages.add(new UserMessage(msg.content()));
                case "assistant" -> messages.add(new AssistantMessage(msg.content()));
                case "system" -> messages.add(new SystemMessage(msg.content()));
            }
        }
        return messages;
    }
}
