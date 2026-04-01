package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.PlanContext;
import com.rag.domain.conversation.agent.model.RetrievalPlan;

public interface RetrievalPlanner {
    RetrievalPlan plan(PlanContext context);
}
