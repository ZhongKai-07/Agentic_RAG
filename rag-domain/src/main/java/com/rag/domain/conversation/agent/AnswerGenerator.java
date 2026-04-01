package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.GenerationContext;
import com.rag.domain.conversation.model.StreamEvent;
import reactor.core.publisher.Flux;

public interface AnswerGenerator {
    Flux<StreamEvent> generateStream(GenerationContext context);
}
