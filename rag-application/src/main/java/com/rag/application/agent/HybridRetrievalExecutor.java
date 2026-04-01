package com.rag.application.agent;

import com.rag.domain.conversation.agent.RetrievalExecutor;
import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.knowledge.port.EmbeddingPort;
import com.rag.domain.knowledge.port.VectorStorePort;
import com.rag.domain.shared.model.SecurityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class HybridRetrievalExecutor implements RetrievalExecutor {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalExecutor.class);

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;

    public HybridRetrievalExecutor(EmbeddingPort embeddingPort,
                                    VectorStorePort vectorStorePort) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
    }

    @Override
    public List<RetrievalResult> execute(RetrievalPlan plan, SearchFilter filter) {
        Map<String, RetrievalResult> mergedResults = new LinkedHashMap<>();

        for (SubQuery subQuery : plan.subQueries()) {
            try {
                // 1. Embed the query
                float[] queryVector = embeddingPort.embed(subQuery.rewrittenQuery());

                // 2. Build filters
                Map<String, Object> searchFilters = new HashMap<>();
                if (filter.userClearance() != null) {
                    // Include both ALL docs and user's clearance level
                    searchFilters.put("security_level", SecurityLevel.ALL.name());
                }

                // 3. Execute hybrid search
                var searchRequest = new VectorStorePort.HybridSearchRequest(
                    subQuery.rewrittenQuery(), queryVector,
                    searchFilters, plan.topK()
                );
                List<VectorStorePort.SearchHit> hits =
                    vectorStorePort.hybridSearch(filter.indexName(), searchRequest);

                // 4. Convert to RetrievalResult
                for (var hit : hits) {
                    String chunkId = hit.chunkId();
                    if (!mergedResults.containsKey(chunkId)) {
                        Map<String, String> highlightMap = new HashMap<>();
                        if (hit.highlights() != null) {
                            hit.highlights().forEach((k, v) ->
                                highlightMap.put(k, String.join("...", v)));
                        }

                        mergedResults.put(chunkId, new RetrievalResult(
                            chunkId,
                            hit.documentId(),
                            getMetaString(hit.metadata(), "document_title"),
                            hit.content(),
                            getMetaInt(hit.metadata(), "page_number"),
                            getMetaString(hit.metadata(), "section_path"),
                            hit.score(),
                            highlightMap
                        ));
                    }
                }
            } catch (Exception e) {
                log.error("Retrieval failed for sub-query '{}': {}",
                    subQuery.rewrittenQuery(), e.getMessage());
            }
        }

        return new ArrayList<>(mergedResults.values());
    }

    private String getMetaString(Map<String, Object> meta, String key) {
        if (meta == null) return "";
        Object val = meta.get(key);
        return val != null ? val.toString() : "";
    }

    private int getMetaInt(Map<String, Object> meta, String key) {
        if (meta == null) return 0;
        Object val = meta.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (Exception e) { return 0; }
    }
}
