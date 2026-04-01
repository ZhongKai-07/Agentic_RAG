package com.rag.domain.conversation.agent;

import com.rag.domain.conversation.agent.model.RetrievalPlan;
import com.rag.domain.conversation.agent.model.RetrievalResult;
import com.rag.domain.conversation.agent.model.SearchFilter;

import java.util.List;

public interface RetrievalExecutor {
    List<RetrievalResult> execute(RetrievalPlan plan, SearchFilter filter);
}
