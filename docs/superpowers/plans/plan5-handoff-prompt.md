# Plan 5 接力 Prompt

> 复制以下内容到新会话中执行。

---

请执行 `Agentic_RAG/docs/superpowers/plans/2026-04-01-plan5-react-frontend.md` 中定义的 Plan 5（React Frontend）开发计划。

## 背景

这是一个 Java 21 + Spring Boot 3.4.5 + Spring AI 1.0.0 的企业级 RAG 知识库项目。后端 Plan 1-4 已全部完成（身份管理、文档处理流水线、对话与 Agent 引擎）。现在需要实现 Plan 5：React 前端。

## 关键约束

1. **严格对接后端 API** — 所有接口路径、请求/响应格式必须与后端 Controller 完全匹配
2. **SSE 流式对话** — Chat 使用 `POST /api/v1/sessions/{id}/chat` 返回 `text/event-stream`，不能用 EventSource（只支持 GET），必须用 fetch + ReadableStream
3. **WebSocket 通知** — 文档状态变更通过 STOMP over SockJS 推送到 `/topic/documents/{docId}`
4. **设计规范** — 暗色主题，使用 Plan 文档中定义的 Design Tokens（颜色、字体、圆角）
5. **不要额外添加** Plan 文档中未定义的文件、功能、或"改进"

## 执行方式

1. 先完整阅读 Plan 文档：`docs/superpowers/plans/2026-04-01-plan5-react-frontend.md`
2. 再阅读 `CLAUDE.md` 了解项目约定
3. 按 Task 1 → Task 9 的依赖顺序逐个执行
4. Task 1 完成后运行 `npm install` 验证依赖安装
5. 每个 Task 完成后执行 `npx tsc --noEmit` 做类型检查
6. 最终 Task 9 要跑 `npm run build` 全量构建
7. 用 Task 工具跟踪进度

## 注意事项

- Plan 文档中的代码是**完整的、可直接使用的**，不需要自行"优化"或重新设计
- Vite 开发服务器配置了代理：`/api` → `localhost:8080`，`/ws` → `localhost:8080`
- 字体文件需要下载到 `public/fonts/`，如果 Google Fonts CDN 不可用，可以先用系统字体回退
- shadcn/ui 组件可以按需添加（`npx shadcn-ui@latest add button` 等），初始 Plan 不包含
- 测试用户 UUID: `11111111-1111-1111-1111-111111111111`，测试空间 UUID: `22222222-2222-2222-2222-222222222222`（已在数据库中）

开始执行。
