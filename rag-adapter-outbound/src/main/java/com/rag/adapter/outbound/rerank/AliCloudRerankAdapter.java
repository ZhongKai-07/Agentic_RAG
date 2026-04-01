package com.rag.adapter.outbound.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.domain.knowledge.port.RerankPort;
import com.rag.infrastructure.config.ServiceRegistryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Component
@Profile("local")
public class AliCloudRerankAdapter implements RerankPort {

    private static final Logger log = LoggerFactory.getLogger(AliCloudRerankAdapter.class);

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public AliCloudRerankAdapter(ServiceRegistryConfig.RerankProperties props,
                                  ObjectMapper objectMapper) {
        // DashScope rerank uses its own native API, not OpenAI-compatible mode
        this.webClient = WebClient.builder()
            .baseUrl("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
        this.model = props.getModel();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        try {
            // DashScope native rerank API format
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("query", query);
            input.put("documents", documents);

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("return_documents", false);
            parameters.put("top_n", topN);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", input);
            requestBody.put("parameters", parameters);

            String response = webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode output = root.get("output");
            JsonNode results = output != null ? output.get("results") : null;
            List<RerankResult> rerankResults = new ArrayList<>();
            if (results != null && results.isArray()) {
                for (JsonNode r : results) {
                    rerankResults.add(new RerankResult(
                        r.get("index").asInt(),
                        r.get("relevance_score").asDouble()
                    ));
                }
            }
            return rerankResults;
        } catch (Exception e) {
            log.warn("Rerank failed, returning original order: {}", e.getMessage());
            // Graceful degradation: return original order
            List<RerankResult> fallback = new ArrayList<>();
            for (int i = 0; i < Math.min(documents.size(), topN); i++) {
                fallback.add(new RerankResult(i, 1.0 - i * 0.01));
            }
            return fallback;
        }
    }
}
