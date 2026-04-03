package com.rag.adapter.outbound.vectorstore;

import com.rag.domain.knowledge.port.VectorStorePort;
import com.rag.infrastructure.config.ServiceRegistryConfig;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LocalOpenSearchAdapterNullVectorTest {

    @Test
    void executeKnnSearch_withNullVector_returnsEmptyList() throws Exception {
        ServiceRegistryConfig.EmbeddingProperties props = new ServiceRegistryConfig.EmbeddingProperties();
        props.setDimension(1024);

        LocalOpenSearchAdapter adapter = new LocalOpenSearchAdapter(mock(OpenSearchClient.class), props);

        VectorStorePort.HybridSearchRequest request = new VectorStorePort.HybridSearchRequest(
            "test query", null, Map.of(), 10);

        Method method = LocalOpenSearchAdapter.class.getDeclaredMethod(
            "executeKnnSearch", String.class, VectorStorePort.HybridSearchRequest.class, List.class);
        method.setAccessible(true);

        Object result = method.invoke(adapter, "test-index", request, List.of());
        assertThat((List<?>) result).isEmpty();
    }
}
