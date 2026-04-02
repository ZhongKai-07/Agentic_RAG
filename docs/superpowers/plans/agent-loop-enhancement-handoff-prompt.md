# Agent Loop Retrieval Enhancement — 接力 Prompt

> 将以下内容复制粘贴到新会话中，即可继续执行实施计划。

---

## 接力 Prompt（复制以下全部内容）

我需要你执行一组已经过设计、评审、修复的实施计划。项目仓库在 `E:/AI use case/RAG-poc/Agentic RAG/Agentic_RAG`。

### 背景

这是一个企业级 Agentic RAG 知识库项目（Java 21 / Spring Boot 3.4.5 / Spring AI 1.0.0），采用 DDD 六边形架构。我们需要增强 Agent 循环的检索质量和容错能力。

### 设计文档（已评审通过）

- 完整设计 spec：`docs/superpowers/specs/2026-04-02-agent-loop-retrieval-enhancement-design.md`
- 设计评审及修复记录：`docs/superpowers/specs/Review/2026-04-02-agent-loop-retrieval-enhancement-design-review.md`

### 4 个实施计划（已评审通过，可独立执行）

| 计划 | 路径 | 核心内容 |
|------|------|---------|
| Plan 1 | `docs/superpowers/plans/2026-04-02-plan1-planner-enhancement.md` | RetrievalConfig 新增 4 字段 + SpaceMapper 更新 + LlmRetrievalPlanner 全面增强（健壮 JSON、历史注入、有界扩展、跨轮不重复） |
| Plan 2 | `docs/superpowers/plans/2026-04-02-plan2-unified-rerank.md` | LocalOpenSearchAdapter null-guard + AgentOrchestrator 重构（putIfAbsent 去重、存储 lastPlan、统一末尾 Rerank + canonical query） + EvaluationContext 加 spaceConfig 字段 |
| Plan 3 | `docs/superpowers/plans/2026-04-02-plan3-evaluator-early-stopping.md` | LlmRetrievalEvaluator 改写为 6 优先级多准则早停（含 RRF 0.02 阈值 fast-path） |
| Plan 4 | `docs/superpowers/plans/2026-04-02-plan4-fault-tolerance.md` | LLM HTTP ReadTimeout（RestClientCustomizer）+ Embedding 降级 BM25 + Orchestrator 异常边界细化 + Generator onErrorResume |

### 执行顺序

```
Plan 1（必须先执行，提供 RetrievalConfig 基础）
  ↓
Plan 2 ─┐
Plan 3 ─┤ 三者可任意顺序，均依赖 Plan 1
Plan 4 ─┘ Plan 4 也可与 Plan 1 并行（不依赖 RetrievalConfig 新字段）
```

### 执行要求

1. 严格按每个 Plan 文件中的 step-by-step 执行，遵循 TDD 流程（先写测试 → 验证失败 → 写实现 → 验证通过 → 提交）
2. 每个 Task 完成后立刻 `git commit`
3. 跨模块编译时先 `mvn install -pl rag-domain -DskipTests -q` 再编译下游模块
4. 每个 Plan 完成后运行该 Plan 的 Verification 步骤，确保全部测试通过

请先阅读上述 4 个 Plan 文件，确认理解后从 Plan 1 开始执行。
