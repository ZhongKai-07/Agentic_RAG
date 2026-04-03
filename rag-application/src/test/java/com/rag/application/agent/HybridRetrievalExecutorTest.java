package com.rag.application.agent;

import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.knowledge.port.EmbeddingPort;
import com.rag.domain.knowledge.port.VectorStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalExecutorTest {

    @Mock EmbeddingPort embeddingPort;
    @Mock VectorStorePort vectorStorePort;
    HybridRetrievalExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new HybridRetrievalExecutor(embeddingPort, vectorStorePort);
    }

    @Test
    void execute_fallsBackToBm25_whenEmbeddingFails() throws Exception {
        when(embeddingPort.embed(anyString()))
            .thenThrow(new RuntimeException("Embedding service down"));
        when(vectorStorePort.hybridSearch(anyString(), any()))
            .thenReturn(List.of(
                new VectorStorePort.SearchHit(
                    "chunk-1", "doc-1", "BM25 result", 0.02,
                    Map.of(), Map.of())
            ));

        RetrievalPlan plan = new RetrievalPlan(
            List.of(new SubQuery("test query", "search")),
            RetrievalPlan.SearchStrategy.HYBRID, 10);
        SearchFilter filter = new SearchFilter("space-1", null, List.of());

        List<RetrievalResult> results = executor.execute(plan, filter);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo("chunk-1");

        ArgumentCaptor<VectorStorePort.HybridSearchRequest> captor =
            ArgumentCaptor.forClass(VectorStorePort.HybridSearchRequest.class);
        verify(vectorStorePort).hybridSearch(eq("space-1"), captor.capture());
        assertThat(captor.getValue().queryVector()).isNull();
    }

    @Test
    void execute_continuesOtherSubQueries_whenOneSubQueryFails() throws Exception {
        when(embeddingPort.embed("failing query"))
            .thenThrow(new RuntimeException("partial failure"));
        when(embeddingPort.embed("working query"))
            .thenReturn(new float[]{0.1f, 0.2f});
        when(vectorStorePort.hybridSearch(anyString(), any()))
            .thenReturn(List.of(
                new VectorStorePort.SearchHit(
                    "chunk-ok", "doc-1", "result", 0.02,
                    Map.of(), Map.of())
            ));

        RetrievalPlan plan = new RetrievalPlan(
            List.of(
                new SubQuery("failing query", "first"),
                new SubQuery("working query", "second")),
            RetrievalPlan.SearchStrategy.HYBRID, 10);
        SearchFilter filter = new SearchFilter("space-1", null, List.of());

        List<RetrievalResult> results = executor.execute(plan, filter);

        assertThat(results).isNotEmpty();
        verify(vectorStorePort, times(2)).hybridSearch(anyString(), any());
    }
}
