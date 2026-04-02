# Frontend Architecture — 前端架构

## 技术栈

| 技术 | 用途 |
|:---|:---|
| React 18 | UI 框架 |
| TypeScript | 类型安全 |
| Vite | 构建工具 + HMR |
| Zustand | 状态管理 |
| Radix UI (shadcn/ui) | 无头组件库 |
| TailwindCSS | 原子化 CSS |
| Axios | HTTP 客户端 |
| React Router v6 | 路由 |
| STOMP / SockJS | WebSocket 实时通知 |
| EventSource | SSE 流式聊天 |

## 组件架构

```mermaid
graph TD
    subgraph App["App.tsx (路由入口)"]
        Router["React Router v6"]
    end

    subgraph Layout["AppLayout (布局框架)"]
        Header["Header<br/>顶部导航"]
        Sidebar["Sidebar<br/>空间切换"]
        SpaceSelector["SpaceSelector<br/>下拉选择"]
    end

    subgraph Pages["页面"]
        SpacesPage["SpacesPage<br/>知识空间列表"]
        DocsPage["DocumentsPage<br/>文档管理"]
        ChatPage["ChatPage<br/>聊天界面"]
    end

    subgraph SpaceComponents["空间组件"]
        CreateSpace["CreateSpaceDialog"]
        AccessEditor["AccessRuleEditor<br/>BU/TEAM/USER 权限"]
    end

    subgraph DocComponents["文档组件"]
        DocTable["DocumentTable<br/>列表 + 分页"]
        Upload["UploadDialog<br/>文件上传"]
        DocDetail["DocumentDetailDialog<br/>版本历史"]
        BatchTag["BatchTagDialog<br/>批量标签"]
        StatusBadge["StatusBadge<br/>状态徽章"]
    end

    subgraph ChatComponents["聊天组件"]
        SessionList["SessionList<br/>会话历史"]
        Thread["MessageThread<br/>消息列表"]
        Bubble["MessageBubble<br/>消息气泡"]
        Input["ChatInput<br/>输入框"]
        Streaming["StreamingText<br/>打字机效果"]
        CitPanel["CitationPanel<br/>引用侧边栏"]
        CitTag["CitationTag<br/>内联引用"]
        AgentIndicator["AgentThinkingIndicator<br/>THINK → SEARCH → EVAL"]
    end

    subgraph Hooks["自定义 Hooks"]
        useSSE["useSSE<br/>SSE 事件监听"]
        useDocNotif["useDocumentNotification<br/>WebSocket 文档状态"]
    end

    subgraph API["API 层"]
        Client["client.ts<br/>Axios + X-User-Id header"]
        SpaceAPI["spaces.ts"]
        DocAPI["documents.ts"]
        ChatAPI["chat.ts<br/>SSE streaming"]
        UserAPI["users.ts"]
    end

    subgraph Store["Zustand Store"]
        SpaceStore["spaceStore"]
        DocStore["documentStore"]
        ChatStore["chatStore"]
        UserStore["userStore"]
    end

    Router --> Layout
    Layout --> Pages

    SpacesPage --> SpaceComponents
    DocsPage --> DocComponents
    ChatPage --> ChatComponents

    ChatComponents --> Hooks
    DocComponents --> Hooks

    Hooks --> API
    Pages --> Store
    Store --> API
    API --> Client

    style ChatComponents fill:#6a040f,stroke:#e85d04,color:#fff
    style DocComponents fill:#3c1642,stroke:#c77dff,color:#fff
    style SpaceComponents fill:#1b4332,stroke:#52b788,color:#fff
    style API fill:#0a2463,stroke:#3e92cc,color:#fff
    style Store fill:#1a1a2e,stroke:#e94560,color:#fff
```

## SSE 聊天流式通信

```
Frontend (useSSE hook)                    Backend (ChatController)
        │                                        │
        │  POST /api/v1/sessions/{id}/chat       │
        │  Accept: text/event-stream             │
        ├───────────────────────────────────────►│
        │                                        │
        │  event: agent_thinking                 │
        │  data: {"round":1,"content":"..."}     │
        │◄───────────────────────────────────────┤
        │  → AgentThinkingIndicator: "思考中"     │
        │                                        │
        │  event: agent_searching                │
        │  data: {"round":1,"queries":["..."]}   │
        │◄───────────────────────────────────────┤
        │  → AgentThinkingIndicator: "检索中"     │
        │                                        │
        │  event: agent_evaluating               │
        │  data: {"round":1,"sufficient":true}   │
        │◄───────────────────────────────────────┤
        │  → AgentThinkingIndicator: "评估中"     │
        │                                        │
        │  event: content_delta                  │
        │  data: {"delta":"根据"}                 │
        │◄───────────────────────────────────────┤
        │  event: content_delta                  │
        │  data: {"delta":"文档"}                 │
        │◄───────────────────────────────────────┤
        │  → StreamingText: 逐字显示             │
        │                                        │
        │  event: citation                       │
        │  data: {"documentId":"...","title":..} │
        │◄───────────────────────────────────────┤
        │  → CitationPanel: 展示引用              │
        │                                        │
        │  event: done                           │
        │  data: {"messageId":"...","total":3}   │
        │◄───────────────────────────────────────┤
        │  → 消息标记完成                          │
        │                                        │
```

## Vite Dev Proxy

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,  // WebSocket 代理
      },
    },
  },
})
```

开发时前端 `:3000` 自动代理到后端 `:8080`，无需 CORS 配置。
