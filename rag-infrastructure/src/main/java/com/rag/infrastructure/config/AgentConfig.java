package com.rag.infrastructure.config;

import com.rag.domain.conversation.agent.*;
import com.rag.domain.knowledge.port.RerankPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public AgentOrchestrator agentOrchestrator(
            RetrievalPlanner planner,
            RetrievalExecutor executor,
            RetrievalEvaluator evaluator,
            AnswerGenerator generator,
            RerankPort rerankPort) {
        return new AgentOrchestrator(planner, executor, evaluator, generator, rerankPort);
    }
}
