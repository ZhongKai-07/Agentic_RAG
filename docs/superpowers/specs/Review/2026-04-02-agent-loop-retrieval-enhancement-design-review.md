## 2026-04-02 Agent Loop Retrieval Enhancement Design Review

这是对于docs\superpowers\specs\2026-04-02-agent-loop-retrieval-enhancement-design.md的review意见，需要你分析一下，看看哪些是正确的

## 1. 致命缺陷：RRF 分数范围与 rawScoreThreshold=0.5 严重冲突

- 发现问题 ：根据 CLAUDE.md 中的描述，当前系统使用的是 Reciprocal Rank Fusion (k=60) 来合并 BM25 和 KNN 结果。
- 风险分析 ：RRF 的计分公式是 1 / (k + rank) 。这意味着，即使一个文档在两路召回中都排第 1 名，它的极限最高分也只有 1/61 + 1/61 ≈ 0.0327 。设计方案中设定的 rawScoreThreshold 默认值为 0.5 ，这意味着 Fast-path 机制将永远无法被触发 。
- 修改建议 ：将 rawScoreThreshold 的默认值调整为适合 RRF 的区间（例如 0.015 到 0.02 ），或者在配置说明中明确指出这是 RRF 分数阈值，需根据实际 k 值设定。


## 2. 逻辑漏洞：Planner 的“失忆”陷阱（死循环风险）
- 发现问题 ：在 1.4 的 Prompt 设计中，引入了 [Conversation History] ，但 遗漏了当前 ReAct 循环的 Scratchpad（执行暂存器） 。
- 风险分析 ：如果 Round 1 的 Planner 生成了子查询 A，但检索出来的结果 Evaluator 认为不合格（进入 Round 2）。此时 Round 2 的 Planner 根本不知道 Round 1 已经搜过 A 且失败了，极大概率会 原封不动地再次生成子查询 A ，导致系统在多次 Round 中原地踏步，白白浪费 Token 和耗时。

当前 RerankPort.java (line 7) 只收一个纯字符串 query，没有 history。像“它的价格是多少”这种多轮问题，最终 rerank 很可能把 Planner 好不容易改写对的召回结果重新打乱。


- 修改建议 ：必须在 Planner Prompt 中增加 [Previous Attempts] 区域。

[Previous Attempts in Current Round]
- Query: "...", Result: "Found N chunks, but missing aspect X" (从 Evaluator 收集反馈)
*Constraint: Do NOT repeat the exact same queries above.*



## 3. 资源泄漏隐患： CompletableFuture.orTimeout() 无法真正中断 I/O
docs\superpowers\specs\2026-04-02-agent-loop-retrieval-enhancement-design.md用 CompletableFuture.orTimeout() 包住 llmPort.chat()，但当前 AliCloudLlmAdapter.java (line 37) 是同步阻塞的 ChatClient.call()；这个写法只能让等待方超时，不能真正取消底层 HTTP 请求。再加上 design.md:223 (line 223) 只处理同步异常，而当前生成阶段的异常是在 AgentOrchestrator.java (line 92) subscribe() 之后异步发生的，所以“前端一定收到 done 或 error”这个承诺还不够扎实。
- 风险分析 ： orTimeout 仅仅是让当前的 Future 在 30 秒后抛出 TimeoutException 并继续往下走， 它并不会打断底层正在阻塞等待网络响应的线程 （TCP 连接依然挂起）。在高并发且大模型 API 普遍降级时，这会导致应用的 HTTP 线程池和连接池被迅速耗尽。
- 修改建议 ：不要依赖外层包装，必须在底层的 HTTP 客户端级别配置超时。例如通过 rag.services.llm.timeout-seconds 属性去配置 Spring AI ChatClient 底层使用的 RestClient 或 WebClient 的 ReadTimeout 。

## 4. 配置分层的自相矛盾（JSONB vs YAML）：
写了“不改 rag-adapter-outbound”，但 design.md:213 (line 213) 的 keyword-only fallback 又依赖 LocalOpenSearchAdapter 在 queryVector=null 时跳过 KNN。当前 LocalOpenSearchAdapter.java (line 145) 始终会构造 KNN 查询，VectorStorePort.java (line 22) 也没有把 null 定义成合法语义。这个降级路径按当前 spec 是落不了地的。

## 5.逻辑严谨性：跨 Query 比较 Raw Score 的有效性
- 发现问题 ：2.2 跨 Round 累加结果时，采用 incoming.score() > existing.score() 来去重保留最高分。
- 风险分析 ：BM25 分数是基于查询词的 TF-IDF 算出来的， 不同查询词算出的 BM25 分数绝对值没有可比性 （比如查“配置”得分可能是 15，查“内存泄露原理”得分可能是 35）。即便经过 RRF 转换，不同 Query 的 Rank 叠加在数学上也不是严格可比的。
- 修改建议 ：只要 chunkId 相同，直接覆盖或者保留第一次召回的结果即可，无需比较分数。因为所有去重后的结果最终都会送给 Unified Rerank 进行统一打分，前面纠结保留哪个历史分数毫无意义，反而增加逻辑复杂度

建议：
把“分数驱动 fast-path”改成“排名/覆盖度驱动 fast-path”，或者只在 final rerank 后再做 early-stop。
给 end-of-loop rerank 定义一个“canonical rewritten query”，不要再用原始问句。
明确配置分层：RetrievalConfig 继续做空间级 JSONB；rag.services.* 只放全局服务超时/连接参数。
承认 keyword-only fallback 需要改 outbound adapter，并把 queryVector=null 的语义写进 port contract。
timeout 放到 adapter/HTTP client 层，流式阶段用 onErrorResume 统一转成 StreamEvent.error 再 complete。