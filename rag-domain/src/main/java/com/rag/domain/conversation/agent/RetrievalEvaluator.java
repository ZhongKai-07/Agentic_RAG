package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.EvaluationContext;
import com.rag.domain.conversation.agent.model.EvaluationResult;

public interface RetrievalEvaluator {
    EvaluationResult evaluate(EvaluationContext context);
}
