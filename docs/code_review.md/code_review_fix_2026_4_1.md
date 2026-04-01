# 代码评审修复记录 — 2026-04-01

对应评审文件: [code_review_2026_4_1.md](code_review_2026_4_1.md)

---

## P0 — 上线阻塞项

### 1. 权限边界落地

**问题:** SpaceController / DocumentController / ChatController 多个端点不读取或校验 `X-User-Id`，任何人可越权操作。

**修复:**
- `SpaceController`: `createSpace`、`getSpace`、`updateAccessRules` 均新增 `@RequestHeader("X-User-Id") UUID userId`
- `DocumentController`: `list`、`getDocument`、`deleteDocument`、`listVersions`、`retryParse`、`batchUpdateTags`、`batchDelete` 均新增 `userId`，注入 `SpaceApplicationService` 调用 `assertUserHasAccess()`
- `ChatController`: `getSession`、`deleteSession` 新增 `userId`，调用 `assertSessionOwner()` 校验会话归属
- `SpaceApplicationService` 新增 `assertUserHasAccess(UUID userId, UUID spaceId)` 方法，复用 AccessRule 查询，未授权抛 `SecurityException` → 403
- `ChatApplicationService` 新增 `assertSessionOwner(ChatSession session, UUID userId)` 方法

**涉及文件:**
- `rag-adapter-inbound/.../rest/SpaceController.java`
- `rag-adapter-inbound/.../rest/DocumentController.java`
- `rag-adapter-inbound/.../rest/ChatController.java`
- `rag-application/.../identity/SpaceApplicationService.java`
- `rag-application/.../chat/ChatApplicationService.java`

---

### 2. 创建空间后列表不可见

**问题:** `createSpace` 不写 AccessRule，而 `listAccessibleSpaces` 完全依赖 AccessRule 查询，导致创建者看不到自己的空间。

**修复:**
- `SpaceApplicationService.createSpace()` 签名新增 `UUID creatorUserId` 参数
- 创建空间后自动写入一条 `TargetType.USER` / `SecurityLevel.MANAGEMENT` 的 AccessRule
- `SpaceController.createSpace()` 传入 `userId`

**涉及文件:**
- `rag-application/.../identity/SpaceApplicationService.java`
- `rag-adapter-inbound/.../rest/SpaceController.java`

---

### 3. 新 Space 首次索引失败

**问题:** `IndexEventHandler` 先调 `deleteByDocumentId` 清旧 chunk，但新 space 的 OpenSearch index 尚不存在，`deleteByQuery` 抛异常 → 文档状态变 FAILED。

**修复:**
- `LocalOpenSearchAdapter.deleteByDocumentId()` 在执行删除前先调用 `client.indices().exists()` 检查索引是否存在，不存在则直接 return

**涉及文件:**
- `rag-adapter-outbound/.../vectorstore/LocalOpenSearchAdapter.java`

---

### 4. SSE Citation 协议前后端不一致

**问题:** 后端 `CitationEmit(Citation citation)` 序列化后 JSON 多一层 `{"citation": {...}}`，前端按扁平字段解析，导致 citation 面板空白。

**修复:**
- `ChatController` 新增 `toEventData(StreamEvent)` 方法：对 `CitationEmit` 直接返回内部 `Citation` 对象序列化，其他事件类型不变
- SSE 发送时调用 `toEventData(event)` 替代直接序列化 `event`
- 前端无需改动

**涉及文件:**
- `rag-adapter-inbound/.../rest/ChatController.java`

---

## P1 — 功能/稳定性问题

### 5. 解析流未关闭

**问题:** `ParseEventHandler` 中 `fileStoragePort.retrieve()` 返回的 `InputStream` 未关闭，存在资源泄漏。

**修复:**
- `fileStream` 包入 try-with-resources 块

**涉及文件:**
- `rag-application/.../event/ParseEventHandler.java`

---

### 6. 删除文档历史版本文件残留

**问题:** `deleteDocument` 和 `batchDelete` 只清理 `currentVersion` 的文件，历史版本文件残留在磁盘；且未清理 OpenSearch 中的 chunks。

**修复:**
- `DocumentApplicationService` 注入 `SpaceRepository` + `VectorStorePort` 新依赖
- 新增 `deleteAllVersionFiles(UUID documentId)` 私有方法：遍历所有版本删除文件
- 新增 `deleteVectorChunks(UUID spaceId, UUID documentId)` 私有方法：清理 OpenSearch 中对应 chunks
- `deleteDocument` 和 `batchDelete` 调用上述方法

**涉及文件:**
- `rag-application/.../document/DocumentApplicationService.java`

---

### 7. SSE 流资源回收不完整

**问题:** `ChatController.chat()` 中 `.subscribe()` 返回的 `Disposable` 未保存，`SseEmitter` 未注册 `onCompletion/onTimeout` 回调，客户端断开后 LLM 流继续运行。

**修复:**
- 保存 `subscribe()` 返回的 `Disposable`
- 注册 `emitter.onCompletion(disposable::dispose)` — 客户端断开时取消上游 Flux
- 注册 `emitter.onTimeout(() -> { disposable.dispose(); emitter.complete(); })` — 超时时清理

**涉及文件:**
- `rag-adapter-inbound/.../rest/ChatController.java`

---

## P2 — Agent 质量问题

### 8. Agent 失败降级静默产出低质量答案

**问题:**
- `LlmRetrievalPlanner` 解析失败 fallback 到空字符串 query（`fallbackPlan("")`）
- `LlmRetrievalEvaluator` LLM 调用或解析失败直接标记 `sufficient=true`，跳过重试

**修复:**
- **Planner:** `parsePlanResponse` 签名改为接收 `String originalQuery`，所有 fallback 路径（空 sub_queries、JSON 解析异常）均使用原始查询
- **Evaluator LLM 调用失败:** 非最后一轮标记 `insufficient`，允许重试；最后一轮才标 `sufficient` 避免无限循环
- **Evaluator JSON 解析失败:** `parseEvalResponse` 签名改为接收 `String originalQuery`，解析失败标记 `insufficient`

**涉及文件:**
- `rag-application/.../agent/LlmRetrievalPlanner.java`
- `rag-application/.../agent/LlmRetrievalEvaluator.java`

---

## 验证状态

- [x] `mvn compile` 全模块编译通过
- [ ] 权限: 用不同 `X-User-Id` 调用 API，验证越权请求返回 403
- [ ] 空间可见性: 创建空间后立即 GET `/spaces`，确认列表包含新空间
- [ ] 首次索引: 新空间上传第一份文档，确认状态最终为 INDEXED
- [ ] SSE Citation: 发送带引用的问题，检查前端 citation 面板正确渲染
- [ ] 流资源回收: 聊天中关闭浏览器 tab，确认后端 Flux 被 dispose
- [ ] 删除清理: 上传多版本文档后删除，检查磁盘和 OpenSearch 无残留
- [ ] Agent 降级: 模拟 LLM 返回非法 JSON，验证 planner 使用原始 query
