package com.rag.domain.knowledge.port;

import java.util.List;

public interface RerankPort {

    List<RerankResult> rerank(String query, List<String> documents, int topN);

    record RerankResult(int index, double score) {}
}
