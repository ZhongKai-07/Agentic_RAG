package com.rag.adapter.outbound.vectorstore;

import com.rag.domain.knowledge.port.VectorStorePort;
import com.rag.infrastructure.config.ServiceRegistryConfig;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Profile("local")
public class LocalOpenSearchAdapter implements VectorStorePort {

    private final OpenSearchClient client;
    private final int embeddingDimension;

    public LocalOpenSearchAdapter(OpenSearchClient client,
                                   ServiceRegistryConfig.EmbeddingProperties embeddingProperties) {
        this.client = client;
        this.embeddingDimension = embeddingProperties.getDimension();
    }

    @Override
    public void upsertChunks(String indexName, List<ChunkDocument> chunks) {
        try {
            ensureIndexExists(indexName);

            List<BulkOperation> operations = chunks.stream().map(chunk -> {
                Map<String, Object> doc = new HashMap<>();
                doc.put("chunk_id", chunk.chunkId());
                doc.put("document_id", chunk.documentId());
                doc.put("content", chunk.content());
                doc.put("embedding", chunk.embedding());
                if (chunk.metadata() != null) {
                    doc.putAll(chunk.metadata());
                }
                return BulkOperation.of(b -> b.index(
                    IndexOperation.of(idx -> idx.index(indexName).id(chunk.chunkId()).document(doc))
                ));
            }).toList();

            BulkResponse response = client.bulk(BulkRequest.of(b -> b.operations(operations)));
            if (response.errors()) {
                throw new RuntimeException("Bulk index errors: " +
                    response.items().stream()
                        .filter(i -> i.error() != null)
                        .map(i -> i.error().reason())
                        .collect(Collectors.joining("; ")));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to upsert chunks to " + indexName, e);
        }
    }

    @Override
    public void deleteByDocumentId(String indexName, String documentId) {
        try {
            boolean exists = client.indices().exists(
                ExistsRequest.of(e -> e.index(indexName))).value();
            if (!exists) return; // New index has no old data to delete
            client.deleteByQuery(d -> d
                .index(indexName)
                .query(q -> q.term(t -> t.field("document_id").value(FieldValue.of(documentId))))
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete by document_id: " + documentId, e);
        }
    }

    @Override
    public List<SearchHit> hybridSearch(String indexName, HybridSearchRequest request) {
        try {
            List<Query> filterQueries = buildFilterQueries(request.filters());

            List<SearchHit> textHits = executeTextSearch(indexName, request, filterQueries);
            List<SearchHit> knnHits = executeKnnSearch(indexName, request, filterQueries);

            return mergeWithRRF(textHits, knnHits, request.topK());
        } catch (IOException e) {
            throw new RuntimeException("Failed to search " + indexName, e);
        }
    }

    private List<Query> buildFilterQueries(Map<String, Object> filters) {
        List<Query> filterQueries = new ArrayList<>();
        if (filters != null) {
            filters.forEach((key, value) -> {
                if (value instanceof List<?> list) {
                    filterQueries.add(Query.of(q -> q.terms(t -> t
                        .field(key)
                        .terms(tv -> tv.value(list.stream()
                            .map(v -> org.opensearch.client.opensearch._types.FieldValue.of(v.toString()))
                            .toList()))
                    )));
                } else {
                    filterQueries.add(Query.of(q -> q.term(t -> t
                        .field(key).value(FieldValue.of(value.toString()))
                    )));
                }
            });
        }
        return filterQueries;
    }

    private List<SearchHit> executeTextSearch(String indexName, HybridSearchRequest request,
                                               List<Query> filterQueries) throws IOException {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
            .must(Query.of(q -> q.multiMatch(m -> m
                .query(request.query())
                .fields("content^3", "section_path^2", "document_title")
            )));
        if (!filterQueries.isEmpty()) {
            boolBuilder.filter(filterQueries);
        }

        SearchRequest searchRequest = SearchRequest.of(s -> s
            .index(indexName)
            .size(request.topK())
            .query(q -> q.bool(boolBuilder.build()))
            .source(src -> src.filter(f -> f.excludes("embedding")))
            .highlight(h -> h
                .fields("content", hf -> hf
                    .fragmentSize(200).numberOfFragments(3)
                    .preTags("<mark>").postTags("</mark>"))
            )
        );

        return parseSearchResponse(client.search(searchRequest, Map.class));
    }

    private List<SearchHit> executeKnnSearch(String indexName, HybridSearchRequest request,
                                              List<Query> filterQueries) throws IOException {
        // Null vector means embedding service was unavailable — skip KNN, BM25 runs alone
        if (request.queryVector() == null) {
            return List.of();
        }
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
            .must(Query.of(q -> q.knn(k -> k
                .field("embedding")
                .vector(request.queryVector())
                .k(request.topK())
            )));
        if (!filterQueries.isEmpty()) {
            boolBuilder.filter(filterQueries);
        }

        SearchRequest searchRequest = SearchRequest.of(s -> s
            .index(indexName)
            .size(request.topK())
            .query(q -> q.bool(boolBuilder.build()))
            .source(src -> src.filter(f -> f.excludes("embedding")))
        );

        return parseSearchResponse(client.search(searchRequest, Map.class));
    }

    private List<SearchHit> parseSearchResponse(SearchResponse<Map> response) {
        return response.hits().hits().stream().map(hit -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = hit.source();
            Map<String, List<String>> highlights = hit.highlight() != null
                ? hit.highlight() : Map.of();

            return new SearchHit(
                (String) source.getOrDefault("chunk_id", hit.id()),
                (String) source.getOrDefault("document_id", ""),
                (String) source.getOrDefault("content", ""),
                hit.score() != null ? hit.score() : 0.0,
                source,
                highlights
            );
        }).toList();
    }

    /**
     * Reciprocal Rank Fusion: merges two ranked lists by rank position rather than raw score.
     * score(d) = sum(1 / (k + rank)) where k=60 is a standard constant.
     */
    private List<SearchHit> mergeWithRRF(List<SearchHit> textHits, List<SearchHit> knnHits, int topK) {
        final int k = 60;
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, SearchHit> hitMap = new HashMap<>();

        for (int i = 0; i < textHits.size(); i++) {
            String id = textHits.get(i).chunkId();
            rrfScores.merge(id, 1.0 / (k + i + 1), Double::sum);
            hitMap.putIfAbsent(id, textHits.get(i));
        }
        for (int i = 0; i < knnHits.size(); i++) {
            String id = knnHits.get(i).chunkId();
            rrfScores.merge(id, 1.0 / (k + i + 1), Double::sum);
            hitMap.putIfAbsent(id, knnHits.get(i));
        }

        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> {
                SearchHit h = hitMap.get(e.getKey());
                return new SearchHit(h.chunkId(), h.documentId(), h.content(),
                    e.getValue(), h.metadata(), h.highlights());
            })
            .toList();
    }

    private void ensureIndexExists(String indexName) throws IOException {
        boolean exists = client.indices().exists(
            ExistsRequest.of(e -> e.index(indexName))).value();
        if (!exists) {
            client.indices().create(CreateIndexRequest.of(c -> c
                .index(indexName)
                .settings(s -> s
                    .knn(true)
                    .numberOfShards("2")
                    .numberOfReplicas("0")
                )
                .mappings(m -> m
                    .properties("chunk_id", p -> p.keyword(k -> k))
                    .properties("document_id", p -> p.keyword(k -> k))
                    .properties("content", p -> p.text(t -> t))
                    .properties("embedding", p -> p.knnVector(knn -> knn
                        .dimension(embeddingDimension)
                    ))
                    .properties("section_path", p -> p.text(t -> t))
                    .properties("document_title", p -> p.text(t -> t))
                    .properties("extracted_tags", p -> p.object(o -> o.enabled(true)))
                    .properties("security_level", p -> p.keyword(k -> k))
                    .properties("tags", p -> p.keyword(k -> k))
                    .properties("page_number", p -> p.integer(i -> i))
                    .properties("chunk_index", p -> p.integer(i -> i))
                )
            ));
        }
    }

}
