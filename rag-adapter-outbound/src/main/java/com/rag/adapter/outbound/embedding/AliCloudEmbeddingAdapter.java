package com.rag.adapter.outbound.embedding;

import com.rag.domain.knowledge.port.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local")
public class AliCloudEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;

    public AliCloudEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.call(
            new org.springframework.ai.embedding.EmbeddingRequest(
                List.of(text), null));
        return response.getResults().get(0).getOutput();
    }

    private static final int MAX_BATCH_SIZE = 10;
    // DashScope text-embedding-v3: max 8192 tokens per input (~2 chars/token for CJK, ~4 for English)
    // Use conservative limit of 6000 chars to stay safely within 8192 tokens
    private static final int MAX_TEXT_CHARS = 6000;

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        // Sanitize: truncate oversized texts, replace empty with placeholder
        List<String> sanitized = texts.stream()
            .map(this::sanitizeText)
            .toList();

        List<float[]> allResults = new ArrayList<>();
        for (int i = 0; i < sanitized.size(); i += MAX_BATCH_SIZE) {
            List<String> batch = sanitized.subList(i, Math.min(i + MAX_BATCH_SIZE, sanitized.size()));
            EmbeddingResponse response = embeddingModel.call(
                new org.springframework.ai.embedding.EmbeddingRequest(batch, null));
            response.getResults().forEach(r -> allResults.add(r.getOutput()));
        }
        return allResults;
    }

    private String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        if (text.length() > MAX_TEXT_CHARS) {
            return text.substring(0, MAX_TEXT_CHARS);
        }
        return text;
    }
}
