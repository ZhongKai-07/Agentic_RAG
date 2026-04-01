package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.*;
import com.rag.domain.conversation.model.StreamEvent;
import com.rag.domain.knowledge.port.RerankPort;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return Flux.create(sink -> {
            try {
                int maxRounds = request.spaceConfig().maxAgentRounds(DEFAULT_MAX_ROUNDS);
                List<RetrievalResult> allResults = new ArrayList<>();
                List<RetrievalFeedback> feedbacks = new ArrayList<>();

                for (int round = 1; round <= maxRounds; round++) {
                    // 1. THINK — plan retrieval strategy
                    sink.next(StreamEvent.agentThinking(round, "Analyzing query..."));
                    PlanContext planCtx = new PlanContext(
                        request.query(), request.history(),
                        request.spaceConfig(), feedbacks);
                    RetrievalPlan plan = planner.plan(planCtx);

                    // 2. ACT — execute retrieval + rerank
                    List<String> queryTexts = plan.subQueries().stream()
                        .map(SubQuery::rewrittenQuery).toList();
                    sink.next(StreamEvent.agentSearching(round, queryTexts));

                    List<RetrievalResult> roundResults = executor.execute(plan, request.filter());

                    // Rerank if we have results
                    if (!roundResults.isEmpty()) {
                        roundResults = applyRerank(request.query(), roundResults);
                    }
                    allResults.addAll(roundResults);

                    // 3. EVALUATE — check if results are sufficient
                    EvaluationContext evalCtx = new EvaluationContext(
                        request.query(), plan.subQueries(),
                        allResults, round, maxRounds);
                    EvaluationResult eval = evaluator.evaluate(evalCtx);
                    sink.next(StreamEvent.agentEvaluating(round, eval.sufficient()));

                    if (eval.sufficient() || round == maxRounds) {
                        break;
                    }

                    // Not sufficient — add feedback for next round
                    feedbacks.add(new RetrievalFeedback(
                        round, eval.missingAspects(), eval.suggestedNextQueries()));
                }

                // 4. GENERATE — stream answer with citations
                List<RetrievalResult> deduped = deduplicateAndSort(allResults);
                GenerationContext genCtx = new GenerationContext(
                    request.query(), request.history(),
                    deduped, request.spaceLanguage());
                generator.generateStream(genCtx)
                    .doOnNext(sink::next)
                    .doOnComplete(sink::complete)
                    .doOnError(sink::error)
                    .subscribe();

            } catch (Exception e) {
                sink.next(StreamEvent.error("AGENT_ERROR", e.getMessage()));
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

    private List<RetrievalResult> deduplicateAndSort(List<RetrievalResult> results) {
        Map<String, RetrievalResult> seen = new LinkedHashMap<>();
        for (RetrievalResult r : results) {
            seen.putIfAbsent(r.chunkId(), r);
        }
        return new ArrayList<>(seen.values());
    }
}
