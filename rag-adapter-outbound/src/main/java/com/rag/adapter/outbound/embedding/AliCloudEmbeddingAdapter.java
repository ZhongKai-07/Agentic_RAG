package com.rag.adapter.outbound.embedding;

import com.rag.domain.knowledge.port.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse response = embeddingModel.call(
            new org.springframework.ai.embedding.EmbeddingRequest(texts, null));
        return response.getResults().stream()
            .map(r -> r.getOutput())
            .toList();
    }
}
