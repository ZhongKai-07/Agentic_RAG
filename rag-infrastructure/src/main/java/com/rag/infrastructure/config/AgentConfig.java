package com.rag.infrastructure.config;

import com.rag.domain.conversation.agent.*;
import com.rag.domain.knowledge.port.RerankPort;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

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

    @Bean
    public RestClientCustomizer llmReadTimeoutCustomizer(
            ServiceRegistryConfig.LlmProperties llmProperties) {
        return restClientBuilder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofSeconds(llmProperties.getTimeoutSeconds()));
            restClientBuilder.requestFactory(factory);
        };
    }
}
