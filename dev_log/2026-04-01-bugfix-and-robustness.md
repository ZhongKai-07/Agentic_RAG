# 开发日志：Bug 修复 & 鲁棒性增强

**日期：** 2026-04-01 ~ 2026-04-02
**范围：** 代码审查修复 + 集成调试 + 鲁棒性增强

---

## 背景

Plan 1~5 全部完成后，对代码库进行了一次深度审查。共收到 14 条审查意见，经逐条核实：5 条准确、4 条完全错误、2 条夸大、3 条部分正确。基于核实结果制定了修复计划并实施。

## 修复的确认问题

### 1. Upload NPE 防御（P0）
- **文件：** `DocumentMapper.toVersionEntity()`
- **问题：** error_log 中确认 NPE，传入的 `DocumentVersion` 为 null
- **修复：** 添加 `Objects.requireNonNull()` 防御性检查

### 2. 4 个端点缺少权限校验（P0）
- **文件：** `DocumentController`（upload/batchUpload/uploadNewVersion）、`ChatController`（createSession）
- **问题：** 这些端点未调用 `spaceService.assertUserHasAccess()`，任何用户可操作任意空间
- **修复：** 各端点开头添加 `assertUserHasAccess()` 调用

### 3. 流式上传 OOM 修复（P0）
- **文件：** `DocumentController`、`DocumentApplicationService`、`DocumentLifecycleService`
- **问题：** `file.getBytes()` 将最大 100MB 文件整体读入堆内存
- **修复：** 改为 `file.getInputStream()` + `DigestInputStream` 流式计算 SHA-256 checksum

### 4. 混合检索 RRF 归一化（P2）
- **文件：** `LocalOpenSearchAdapter`
- **问题：** BM25 和 KNN 用 `should` 直接合并，分数量纲不同
- **修复：** 拆分为两个独立查询，用 Reciprocal Rank Fusion（k=60）合并排序

### 5. 向量维度配置化（P2）
- **文件：** `ServiceRegistryConfig`、`LocalOpenSearchAdapter`、`application-local.yml`
- **问题：** `.dimension(1024)` 硬编码
- **修复：** `EmbeddingProperties` 新增 `dimension` 字段，从 YAML 读取

## 鲁棒性增强

### MDC 请求追踪
- 新建 `CorrelationIdFilter`：读取 `X-Correlation-Id` 或自动生成 8 位 UUID
- `AsyncConfig` 两个线程池添加 `TaskDecorator` 传播 MDC
- `GlobalExceptionHandler` 所有错误响应包含 `requestId`
- `application.yml` 日志格式包含 `[%X{correlationId}]`

### 领域异常体系
- 新建 `KnowledgeBaseEmptyException`（空知识库查询时抛出）
- `GlobalExceptionHandler` 新增处理：返回 404 + `KNOWLEDGE_BASE_EMPTY`
- `AgentOrchestrator` 添加输入校验（`Objects.requireNonNull`）

### Agent 循环可观测性
- `ChatApplicationService.doOnNext` 按 StreamEvent 类型输出结构化日志
- 包含 session ID、round 轮次、查询数量、评估结果

## 集成调试（踩坑记录）

### docling-serve 0.5.1 API 变更
- **现象：** 上传文件后解析失败
- **根因：** docling 0.5.x 端点从 `/v1/convert` 改为 `/v1alpha/convert/file`，响应格式从结构化 `main_text[]` 改为 `md_content` Markdown 字符串
- **修复：** 重写 `DoclingJavaAdapter`，URI 改为 `/v1alpha/convert/file`，解析 `md_content`，按 Markdown 标题语义分块

### DashScope Embedding 批量限制
- **现象：** 18 chunks 一次性调 embedding API 报 400（batch size > 10）
- **修复：** `AliCloudEmbeddingAdapter.embedBatch()` 按 10 条分批

### DashScope Embedding 文本长度限制
- **现象：** 分批后仍报 400（input length > 8192）
- **修复：** 超长文本截断到 6000 字符，空文本替换为占位符。`estimateTokens` 从 `length/3` 改为 `length/2`

### 文档状态机 retry 死锁
- **现象：** retry 失败文档时报 `Cannot transition from FAILED to PARSING`
- **根因：** 状态机不允许 `FAILED → PARSING`，且 catch 块试图 `FAILED → FAILED` 二次崩溃
- **修复：** `DocumentStatus.canTransitionTo` 允许 `FAILED → PARSING`；catch 块检查当前状态

### Spring AI base-url vs 手动 WebClient base-url
- **现象：** LLM/Embedding 调用报 404（`/v1/v1/chat/completions`）
- **根因：** `spring.ai.openai.base-url` 设为带 `/v1` 的地址，Spring AI 又自动追加 `/v1`
- **修复：** `spring.ai.openai.base-url` 不带 `/v1`

### DashScope Rerank API 不走 OpenAI 兼容模式
- **现象：** Rerank 调用持续 404
- **根因：** DashScope rerank 使用原生 API（`/api/v1/services/rerank/...`），请求体格式也不同
- **修复：** `AliCloudRerankAdapter` 硬编码正确 URL，请求体改为 `{model, input: {query, documents}, parameters: {top_n}}`

### LLM 流式生成 Connection reset
- **现象：** Agent 3 轮检索后流式生成被服务端断连
- **根因：** 所有检索结果拼入 prompt 超过模型 context window
- **修复：** 限制 top 8 条结果 + 每条内容截断 1500 字符

## 审查意见核实结论

| 审查意见 | 核实结论 |
|---------|---------|
| 控制器完全没校验 X-User-Id | 夸大，4 个端点缺失但大部分有 |
| 创建空间未写入 AccessRule | 完全错误，代码明确创建了 |
| SSE Citation JSON 前后端不一致 | 完全错误，字段完全对齐 |
| Agent 空查询幻觉 | 完全错误，fallback 使用原始 query |
| 僵尸文件泄漏 | 完全错误，`deleteAllVersionFiles()` 遍历删除 |
| 线程泄漏 | 完全错误，有 Disposable + onTimeout 处理 |
| file.getBytes() OOM | 正确，已修复 |
| 混合检索无 RRF | 正确，已修复 |
| 向量维度硬编码 | 正确，已修复 |
| Upload NPE | 部分正确（NPE 存在但根因分析有误） |
| 空空间查询崩溃 | 误导性（异常是有意设计，消息友好） |

## 变更文件清单

| 文件 | 变更类型 |
|------|---------|
| `DocumentMapper.java` | 修改 — NPE 防御 |
| `DocumentController.java` | 修改 — 权限校验 + 流式上传 |
| `ChatController.java` | 修改 — 注入 SpaceService + 权限校验 |
| `DocumentApplicationService.java` | 修改 — InputStream 签名 + DigestInputStream |
| `DocumentLifecycleService.java` | 修改 — 流式 checksum 方法 |
| `LocalOpenSearchAdapter.java` | 修改 — RRF + 配置化维度 |
| `ServiceRegistryConfig.java` | 修改 — EmbeddingProperties.dimension |
| `GlobalExceptionHandler.java` | 修改 — errorBody + KnowledgeBaseEmptyException |
| `AgentOrchestrator.java` | 修改 — 输入校验 + catch 加固 |
| `HybridRetrievalExecutor.java` | 修改 — KnowledgeBaseEmptyException |
| `ChatApplicationService.java` | 修改 — Agent 循环日志 |
| `AsyncConfig.java` | 修改 — MDC TaskDecorator |
| `AliCloudEmbeddingAdapter.java` | 修改 — 分批 + 截断 |
| `AliCloudRerankAdapter.java` | 修改 — DashScope 原生 API |
| `DoclingJavaAdapter.java` | 修改 — v1alpha API + md_content 解析 |
| `LlmAnswerGenerator.java` | 修改 — top 8 + 内容截断 |
| `DocumentStatus.java` | 修改 — FAILED→PARSING |
| `ParseEventHandler.java` | 修改 — catch 块防二次崩溃 |
| `CorrelationIdFilter.java` | 新建 |
| `KnowledgeBaseEmptyException.java` | 新建 |
| `application.yml` | 修改 — 日志格式 |
| `application-local.yml` | 修改 — dimension 配置 |
| `CLAUDE.md` | 修改 — 新增 patterns + gotchas |
| `README.md` | 修改 — 启动指南 + 用户手册 + 当前状态 |
