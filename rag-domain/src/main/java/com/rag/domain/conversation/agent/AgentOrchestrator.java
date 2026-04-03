package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.conversation.model.StreamEvent;
import com.rag.domain.knowledge.exception.KnowledgeBaseEmptyException;
import com.rag.domain.knowledge.port.RerankPort;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AgentOrchestrator {

    private static final int DEFAULT_MAX_ROUNDS = 3;

    private final RetrievalPlanner planner;
    private final RetrievalExecutor executor;
    private final RetrievalEvaluator evaluator;
    private final AnswerGenerator generator;
    private final RerankPort rerankPort;

    public AgentOrchestrator(RetrievalPlanner planner,
                              RetrievalExecutor executor,
                              RetrievalEvaluator evaluator,
                              AnswerGenerator generator,
                              RerankPort rerankPort) {
        this.planner = planner;
        this.executor = executor;
        this.evaluator = evaluator;
        this.generator = generator;
        this.rerankPort = rerankPort;
    }

    public Flux<StreamEvent> orchestrate(AgentRequest request) {
        Objects.requireNonNull(request, "AgentRequest must not be null");
        Objects.requireNonNull(request.query(), "Query must not be null");
        Objects.requireNonNull(request.filter(), "SearchFilter must not be null");
        if (request.query().isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }

        return Flux.create(sink -> {
            Map<String, RetrievalResult> mergedResults = new LinkedHashMap<>();
            RetrievalPlan lastPlan = null;
            try {
                int maxRounds = request.spaceConfig().maxAgentRounds(DEFAULT_MAX_ROUNDS);
                List<RetrievalFeedback> feedbacks = new ArrayList<>();

                for (int round = 1; round <= maxRounds; round++) {
                    // 1. THINK — plan retrieval strategy
                    sink.next(StreamEvent.agentThinking(round, "Analyzing query..."));
                    PlanContext planCtx = new PlanContext(
                        request.query(), request.history(),
                        request.spaceConfig(), feedbacks);
                    RetrievalPlan plan = planner.plan(planCtx);
                    lastPlan = plan;

                    // 2. ACT — execute retrieval (no per-round rerank)
                    List<String> queryTexts = plan.subQueries().stream()
                        .map(SubQuery::rewrittenQuery).toList();
                    sink.next(StreamEvent.agentSearching(round, queryTexts));

                    List<RetrievalResult> roundResults = executor.execute(plan, request.filter());

                    // Deduplicate: first occurrence wins
                    for (RetrievalResult r : roundResults) {
                        mergedResults.putIfAbsent(r.chunkId(), r);
                    }

                    // 3. EVALUATE — check if results are sufficient
                    List<RetrievalResult> currentResults = new ArrayList<>(mergedResults.values());
                    EvaluationContext evalCtx = new EvaluationContext(
                        request.query(), plan.subQueries(),
                        currentResults, round, maxRounds,
                        request.spaceConfig());
                    EvaluationResult eval = evaluator.evaluate(evalCtx);
                    sink.next(StreamEvent.agentEvaluating(round, eval.sufficient()));

                    if (eval.sufficient() || round == maxRounds) {
                        break;
                    }

                    // Not sufficient — add feedback for next round
                    feedbacks.add(new RetrievalFeedback(
                        round, eval.missingAspects(), eval.suggestedNextQueries()));
                }

                // 4. Unified rerank after all rounds using canonical rewritten query
                List<RetrievalResult> deduped = new ArrayList<>(mergedResults.values());
                if (!deduped.isEmpty() && lastPlan != null) {
                    String canonicalQuery = lastPlan.subQueries().get(0).rewrittenQuery();
                    deduped = applyRerank(canonicalQuery, deduped);
                }

                // 5. GENERATE — stream answer with citations
                GenerationContext genCtx = new GenerationContext(
                    request.query(), request.history(),
                    deduped, request.spaceLanguage());
                generator.generateStream(genCtx)
                    .doOnNext(sink::next)
                    .doOnComplete(sink::complete)
                    .doOnError(sink::error)
                    .subscribe();

            } catch (KnowledgeBaseEmptyException e) {
                if (!mergedResults.isEmpty()) {
                    // Partial results from earlier rounds — proceed to generate
                    try {
                        List<RetrievalResult> partial = new ArrayList<>(mergedResults.values());
                        if (lastPlan != null) {
                            String canonicalQuery = lastPlan.subQueries().get(0).rewrittenQuery();
                            partial = applyRerank(canonicalQuery, partial);
                        }
                        GenerationContext genCtx = new GenerationContext(
                            request.query(), request.history(), partial, request.spaceLanguage());
                        generator.generateStream(genCtx)
                            .doOnNext(sink::next)
                            .doOnComplete(sink::complete)
                            .doOnError(err -> {
                                sink.next(StreamEvent.error("GENERATOR_ERROR", err.getMessage()));
                                sink.complete();
                            })
                            .subscribe();
                    } catch (Exception genEx) {
                        sink.next(StreamEvent.error("AGENT_ERROR", genEx.getMessage()));
                        sink.complete();
                    }
                } else {
                    sink.next(StreamEvent.error("KNOWLEDGE_BASE_EMPTY", e.getMessage()));
                    sink.complete();
                }
            } catch (Exception e) {
                try {
                    sink.next(StreamEvent.error("AGENT_ERROR", e.getMessage()));
                } catch (Exception ignored) {
                }
                sink.complete();
            }
        });
    }

    private List<RetrievalResult> applyRerank(String query, List<RetrievalResult> results) {
        List<String> contents = results.stream().map(RetrievalResult::content).toList();
        int topN = Math.min(results.size(), 10);
        List<RerankPort.RerankResult> reranked = rerankPort.rerank(query, contents, topN);
        return reranked.stream()
            .map(rr -> results.get(rr.index()))
            .toList();
    }
}
