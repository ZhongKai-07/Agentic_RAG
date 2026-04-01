# Plan 5: React Frontend

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the React frontend for the Agentic RAG Knowledge Base. Three core pages: Chat (SSE streaming Q&A with citations), Documents (upload/manage/monitor), Spaces (create/configure). Dark theme UI with agent thinking indicators, typewriter streaming, and real-time document status via WebSocket.

**Tech Stack:** React 18 + TypeScript, Vite, shadcn/ui (Radix + Tailwind CSS 4), Zustand (state), SockJS + STOMP (WebSocket notifications), native EventSource (SSE chat streaming)

**Depends on:** Plan 1-4 (all backend APIs must be functional)

**Backend API Summary (all under `/api/v1/`):**

| Area | Endpoints |
|------|-----------|
| User | `GET /users/me` |
| Spaces | `POST /spaces`, `GET /spaces`, `GET /spaces/{id}`, `PUT /spaces/{id}/access-rules` |
| Documents | `POST /spaces/{id}/documents/upload`, `POST .../batch-upload`, `GET .../documents?page&size&search`, `GET .../documents/{id}`, `DELETE .../documents/{id}`, `POST .../documents/{id}/versions`, `GET .../documents/{id}/versions`, `POST .../documents/{id}/retry`, `PUT .../documents/batch-tags`, `DELETE .../documents/batch-delete` |
| Chat | `POST /spaces/{id}/sessions`, `GET /spaces/{id}/sessions`, `GET /sessions/{id}`, `DELETE /sessions/{id}`, `POST /sessions/{id}/chat` (SSE) |
| Health | `GET /health` |
| WebSocket | `ws://host:8080/ws/notifications` (STOMP), topic: `/topic/documents/{docId}` |

**Design Tokens:**

```
Background: #0A0A0F / #12121A / #1A1A26
Text: #E8E6F0 / #9B97AD / #5C586E
Accent: #6C8EFF (blue) / #A78BFA (purple) / #34D399 (green)
Status: #F59E0B (parsing) / #34D399 (indexed) / #EF4444 (failed)
Citation: bg #1E1B2E / border #3B3558
Radius: 6/10/16px, Fonts: Bricolage Grotesque (headings), IBM Plex Sans (body), JetBrains Mono (code)
```

---

## File Structure

```
rag-frontend/
├── index.html
├── package.json
├── tsconfig.json
├── tsconfig.app.json
├── tsconfig.node.json
├── vite.config.ts
├── tailwind.config.ts
├── components.json                          # shadcn/ui config
├── public/
│   └── fonts/
│       ├── bricolage-grotesque-800.woff2
│       ├── bricolage-grotesque-900.woff2
│       ├── ibm-plex-sans-200.woff2
│       ├── ibm-plex-sans-400.woff2
│       └── jetbrains-mono-400.woff2
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── index.css                            # Tailwind + fonts + design tokens
│   ├── lib/
│   │   └── utils.ts                         # cn() helper
│   ├── api/
│   │   ├── client.ts                        # fetch wrapper with X-User-Id
│   │   ├── users.ts                         # GET /users/me
│   │   ├── spaces.ts                        # Space CRUD
│   │   ├── documents.ts                     # Document CRUD + upload
│   │   ├── chat.ts                          # Session CRUD + SSE streaming
│   │   └── types.ts                         # API response types
│   ├── stores/
│   │   ├── useAuthStore.ts                  # userId, user profile
│   │   ├── useSpaceStore.ts                 # current space, space list
│   │   ├── useChatStore.ts                  # sessions, messages, streaming state
│   │   └── useDocumentStore.ts              # documents, upload progress
│   ├── hooks/
│   │   ├── useSSE.ts                        # SSE streaming hook for chat
│   │   └── useDocumentNotification.ts       # WebSocket STOMP hook
│   ├── components/
│   │   ├── ui/                              # shadcn/ui primitives (auto-generated)
│   │   ├── layout/
│   │   │   ├── AppLayout.tsx                # Top-level shell: header + content
│   │   │   ├── Header.tsx                   # Logo, space selector, user avatar
│   │   │   └── SpaceSelector.tsx            # Dropdown to switch spaces
│   │   ├── chat/
│   │   │   ├── ChatPage.tsx                 # 3-column layout: sessions | messages | citations
│   │   │   ├── SessionList.tsx              # Left sidebar: session list + new button
│   │   │   ├── MessageThread.tsx            # Center: message bubbles
│   │   │   ├── MessageBubble.tsx            # Single message (user or assistant)
│   │   │   ├── AgentThinkingIndicator.tsx   # Thinking/searching/evaluating status
│   │   │   ├── StreamingText.tsx            # Typewriter rendering of content_delta
│   │   │   ├── CitationTag.tsx              # Inline [1] [2] clickable tag
│   │   │   ├── CitationPanel.tsx            # Right sidebar: citation details
│   │   │   └── ChatInput.tsx               # Input box + send button
│   │   ├── documents/
│   │   │   ├── DocumentsPage.tsx            # Table + toolbar
│   │   │   ├── DocumentTable.tsx            # Data table with checkbox select
│   │   │   ├── DocumentRow.tsx              # Single row with status badge
│   │   │   ├── StatusBadge.tsx              # UPLOADED/PARSING/INDEXED/FAILED
│   │   │   ├── UploadDialog.tsx             # File upload modal
│   │   │   ├── DocumentDetailDialog.tsx     # Document info + version history
│   │   │   ├── BatchTagDialog.tsx           # Batch tag editor
│   │   │   └── UploadProgress.tsx           # Real-time parse/index progress (WebSocket)
│   │   └── spaces/
│   │       ├── SpacesPage.tsx               # Space list + create
│   │       ├── CreateSpaceDialog.tsx         # Create space form
│   │       └── AccessRuleEditor.tsx         # BU/TEAM/USER rule editor
│   └── pages/
│       └── LoginPage.tsx                    # Mock login: pick userId
```

---

### Task 1: Project Scaffolding + Tailwind + shadcn/ui

**Files:**
- Create: `rag-frontend/package.json`
- Create: `rag-frontend/vite.config.ts`
- Create: `rag-frontend/tsconfig.json`, `tsconfig.app.json`, `tsconfig.node.json`
- Create: `rag-frontend/tailwind.config.ts`
- Create: `rag-frontend/components.json`
- Create: `rag-frontend/index.html`
- Create: `rag-frontend/src/main.tsx`
- Create: `rag-frontend/src/index.css`
- Create: `rag-frontend/src/lib/utils.ts`
- Create: `rag-frontend/src/App.tsx`

- [ ] **Step 1: Create package.json**

`rag-frontend/package.json`:
```json
{
  "name": "rag-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "@radix-ui/react-avatar": "^1.1.0",
    "@radix-ui/react-dialog": "^1.1.0",
    "@radix-ui/react-dropdown-menu": "^2.1.0",
    "@radix-ui/react-popover": "^1.1.0",
    "@radix-ui/react-scroll-area": "^1.2.0",
    "@radix-ui/react-select": "^2.1.0",
    "@radix-ui/react-separator": "^1.1.0",
    "@radix-ui/react-slot": "^1.1.0",
    "@radix-ui/react-tooltip": "^1.1.0",
    "@stomp/stompjs": "^7.0.0",
    "class-variance-authority": "^0.7.0",
    "clsx": "^2.1.0",
    "lucide-react": "^0.400.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.23.0",
    "sockjs-client": "^1.6.1",
    "tailwind-merge": "^2.3.0",
    "zustand": "^4.5.0"
  },
  "devDependencies": {
    "@types/react": "^18.3.0",
    "@types/react-dom": "^18.3.0",
    "@types/sockjs-client": "^1.5.4",
    "@vitejs/plugin-react": "^4.3.0",
    "autoprefixer": "^10.4.19",
    "postcss": "^8.4.38",
    "tailwindcss": "^3.4.4",
    "typescript": "^5.5.0",
    "vite": "^5.4.0"
  }
}
```

- [ ] **Step 2: Create vite.config.ts**

`rag-frontend/vite.config.ts`:
```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
      },
    },
  },
})
```

- [ ] **Step 3: Create TypeScript configs**

`rag-frontend/tsconfig.json`:
```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

`rag-frontend/tsconfig.app.json`:
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src"]
}
```

`rag-frontend/tsconfig.node.json`:
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2023"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 4: Create Tailwind config with design tokens**

`rag-frontend/tailwind.config.ts`:
```typescript
import type { Config } from 'tailwindcss'

const config: Config = {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: {
          primary: '#0A0A0F',
          secondary: '#12121A',
          tertiary: '#1A1A26',
        },
        text: {
          primary: '#E8E6F0',
          secondary: '#9B97AD',
          muted: '#5C586E',
        },
        accent: {
          blue: '#6C8EFF',
          purple: '#A78BFA',
          green: '#34D399',
        },
        status: {
          parsing: '#F59E0B',
          indexed: '#34D399',
          failed: '#EF4444',
          uploading: '#6C8EFF',
        },
        citation: {
          bg: '#1E1B2E',
          border: '#3B3558',
          hover: '#2A2640',
        },
      },
      fontFamily: {
        heading: ['Bricolage Grotesque', 'sans-serif'],
        body: ['IBM Plex Sans', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      fontSize: {
        hero: '3rem',
        h1: '2.25rem',
        h2: '1.5rem',
        body: '1rem',
        caption: '0.8125rem',
        code: '0.875rem',
      },
      borderRadius: {
        sm: '6px',
        md: '10px',
        lg: '16px',
        pill: '9999px',
      },
    },
  },
  plugins: [],
}

export default config
```

- [ ] **Step 5: Create postcss.config.js**

`rag-frontend/postcss.config.js`:
```javascript
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
```

- [ ] **Step 6: Create components.json (shadcn/ui)**

`rag-frontend/components.json`:
```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "default",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "tailwind.config.ts",
    "css": "src/index.css",
    "baseColor": "zinc",
    "cssVariables": false
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils"
  }
}
```

- [ ] **Step 7: Create index.html**

`rag-frontend/index.html`:
```html
<!DOCTYPE html>
<html lang="zh-CN" class="dark">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>RAG Knowledge Base</title>
  </head>
  <body class="bg-bg-primary text-text-primary font-body">
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 8: Create index.css with font-face and Tailwind directives**

`rag-frontend/src/index.css`:
```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@font-face {
  font-family: 'Bricolage Grotesque';
  src: url('/fonts/bricolage-grotesque-800.woff2') format('woff2');
  font-weight: 800;
  font-display: swap;
}

@font-face {
  font-family: 'Bricolage Grotesque';
  src: url('/fonts/bricolage-grotesque-900.woff2') format('woff2');
  font-weight: 900;
  font-display: swap;
}

@font-face {
  font-family: 'IBM Plex Sans';
  src: url('/fonts/ibm-plex-sans-200.woff2') format('woff2');
  font-weight: 200;
  font-display: swap;
}

@font-face {
  font-family: 'IBM Plex Sans';
  src: url('/fonts/ibm-plex-sans-400.woff2') format('woff2');
  font-weight: 400;
  font-display: swap;
}

@font-face {
  font-family: 'JetBrains Mono';
  src: url('/fonts/jetbrains-mono-400.woff2') format('woff2');
  font-weight: 400;
  font-display: swap;
}

@layer base {
  * {
    @apply border-citation-border;
  }

  body {
    @apply bg-bg-primary text-text-primary font-body antialiased;
  }

  ::-webkit-scrollbar {
    width: 6px;
  }

  ::-webkit-scrollbar-track {
    background: #12121A;
  }

  ::-webkit-scrollbar-thumb {
    background: #3B3558;
    border-radius: 3px;
  }
}
```

- [ ] **Step 9: Create lib/utils.ts**

`rag-frontend/src/lib/utils.ts`:
```typescript
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

- [ ] **Step 10: Create main.tsx and App.tsx (skeleton)**

`rag-frontend/src/main.tsx`:
```typescript
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
)
```

`rag-frontend/src/App.tsx`:
```typescript
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'
import { AppLayout } from '@/components/layout/AppLayout'
import { ChatPage } from '@/components/chat/ChatPage'
import { DocumentsPage } from '@/components/documents/DocumentsPage'
import { SpacesPage } from '@/components/spaces/SpacesPage'
import { LoginPage } from '@/pages/LoginPage'

export default function App() {
  const userId = useAuthStore((s) => s.userId)

  if (!userId) {
    return <LoginPage />
  }

  return (
    <AppLayout>
      <Routes>
        <Route path="/" element={<Navigate to="/chat" replace />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/documents" element={<DocumentsPage />} />
        <Route path="/spaces" element={<SpacesPage />} />
      </Routes>
    </AppLayout>
  )
}
```

- [ ] **Step 11: Install dependencies and verify**

```bash
cd "E:/AI Application/Agentic_RAG/rag-frontend" && npm install && npm run build && echo "OK"
```

Note: `npm run build` will fail until all components exist. Use `npx tsc --noEmit` to check types incrementally, or just verify `npm install` succeeds.

- [ ] **Step 12: Commit**

```bash
git add rag-frontend/package.json rag-frontend/vite.config.ts rag-frontend/tsconfig*.json \
        rag-frontend/tailwind.config.ts rag-frontend/postcss.config.js rag-frontend/components.json \
        rag-frontend/index.html rag-frontend/src/main.tsx rag-frontend/src/App.tsx \
        rag-frontend/src/index.css rag-frontend/src/lib/utils.ts
git commit -m "feat(frontend): scaffold React project with Vite, Tailwind, shadcn/ui, design tokens"
```

---

### Task 2: API Client + Types

**Files:**
- Create: `rag-frontend/src/api/types.ts`
- Create: `rag-frontend/src/api/client.ts`
- Create: `rag-frontend/src/api/users.ts`
- Create: `rag-frontend/src/api/spaces.ts`
- Create: `rag-frontend/src/api/documents.ts`
- Create: `rag-frontend/src/api/chat.ts`

- [ ] **Step 1: Create API response types**

All types mirror the backend DTOs exactly.

`rag-frontend/src/api/types.ts`:
```typescript
// === User ===
export interface UserResponse {
  userId: string
  username: string
  displayName: string
  email: string
  bu: string
  team: string
  role: 'ADMIN' | 'MANAGER' | 'MEMBER'
}

// === Space ===
export interface SpaceResponse {
  spaceId: string
  name: string
  description: string
  ownerTeam: string
  language: string
  indexName: string
  status: string
  accessRules: AccessRuleResponse[]
  createdAt: string
  updatedAt: string
}

export interface AccessRuleResponse {
  ruleId: string
  targetType: 'BU' | 'TEAM' | 'USER'
  targetValue: string
  docSecurityClearance: 'ALL' | 'MANAGEMENT'
}

export interface CreateSpaceRequest {
  name: string
  description?: string
  ownerTeam: string
  language: string
  indexName: string
}

export interface AccessRuleInput {
  targetType: string
  targetValue: string
  docSecurityClearance?: string
}

// === Document ===
export interface DocumentResponse {
  documentId: string
  spaceId: string
  title: string
  fileType: string
  securityLevel: 'ALL' | 'MANAGEMENT'
  status: 'UPLOADED' | 'PARSING' | 'PARSED' | 'INDEXING' | 'INDEXED' | 'FAILED'
  chunkCount: number
  currentVersionNo: string
  tags: string[]
  uploadedBy: string
  createdAt: string
  updatedAt: string
}

export interface DocumentDetailResponse extends DocumentResponse {
  versions: VersionResponse[]
}

export interface VersionResponse {
  versionId: string
  versionNo: number
  filePath: string
  fileSize: number
  checksum: string
  createdAt: string
  createdBy: string
}

export interface PageResult<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// === Chat ===
export interface SessionResponse {
  sessionId: string
  userId: string
  spaceId: string
  title: string
  status: 'ACTIVE' | 'ARCHIVED'
  messageCount: number
  createdAt: string
  lastActiveAt: string
}

export interface SessionDetailResponse {
  session: SessionResponse
  messages: MessageResponse[]
}

export interface MessageResponse {
  messageId: string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  citations: CitationResponse[]
  tokenCount: number
  createdAt: string
}

export interface CitationResponse {
  citationIndex: number
  documentId: string
  documentTitle: string
  chunkId: string
  pageNumber: number | null
  sectionPath: string
  snippet: string
}

// === SSE Events ===
export type StreamEventType =
  | 'agent_thinking'
  | 'agent_searching'
  | 'agent_evaluating'
  | 'content_delta'
  | 'citation'
  | 'done'
  | 'error'

export interface AgentThinkingEvent {
  round: number
  content: string
}

export interface AgentSearchingEvent {
  round: number
  queries: string[]
}

export interface AgentEvaluatingEvent {
  round: number
  sufficient: boolean
}

export interface ContentDeltaEvent {
  delta: string
}

export interface CitationEmitEvent {
  citationIndex: number
  documentId: string
  documentTitle: string
  chunkId: string
  pageNumber: number | null
  sectionPath: string
  snippet: string
}

export interface DoneEvent {
  messageId: string
  totalCitations: number
}

export interface ErrorEvent {
  code: string
  message: string
}

// === WebSocket ===
export interface DocumentStatusEvent {
  type: 'DOCUMENT_STATUS_CHANGED'
  payload: {
    documentId: string
    status: string
    progress: number
    message: string
  }
}

// === Error ===
export interface ApiError {
  error: string
  message: string
  timestamp: string
}
```

- [ ] **Step 2: Create API client with X-User-Id header**

`rag-frontend/src/api/client.ts`:
```typescript
import { useAuthStore } from '@/stores/useAuthStore'
import type { ApiError } from './types'

const BASE = '/api/v1'

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const userId = useAuthStore.getState().userId
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  }
  if (userId) {
    headers['X-User-Id'] = userId
  }
  // Only set Content-Type for non-FormData bodies
  if (options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }

  const res = await fetch(`${BASE}${path}`, { ...options, headers })

  if (!res.ok) {
    const err: ApiError = await res.json().catch(() => ({
      error: 'UNKNOWN',
      message: res.statusText,
      timestamp: new Date().toISOString(),
    }))
    throw err
  }

  if (res.status === 204) return undefined as T
  return res.json()
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'DELETE',
      body: body ? JSON.stringify(body) : undefined,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
    }),
}
```

- [ ] **Step 3: Create domain API modules**

`rag-frontend/src/api/users.ts`:
```typescript
import { api } from './client'
import type { UserResponse } from './types'

export const usersApi = {
  getMe: () => api.get<UserResponse>('/users/me'),
}
```

`rag-frontend/src/api/spaces.ts`:
```typescript
import { api } from './client'
import type { SpaceResponse, CreateSpaceRequest, AccessRuleInput } from './types'

export const spacesApi = {
  create: (req: CreateSpaceRequest) => api.post<SpaceResponse>('/spaces', req),
  list: () => api.get<SpaceResponse[]>('/spaces'),
  get: (spaceId: string) => api.get<SpaceResponse>(`/spaces/${spaceId}`),
  updateAccessRules: (spaceId: string, rules: AccessRuleInput[]) =>
    api.put<SpaceResponse>(`/spaces/${spaceId}/access-rules`, { rules }),
}
```

`rag-frontend/src/api/documents.ts`:
```typescript
import { api } from './client'
import type {
  DocumentResponse,
  DocumentDetailResponse,
  VersionResponse,
  PageResult,
} from './types'

export const documentsApi = {
  upload: (spaceId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<DocumentResponse>(`/spaces/${spaceId}/documents/upload`, form)
  },

  batchUpload: (spaceId: string, files: File[]) => {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return api.post<DocumentResponse[]>(`/spaces/${spaceId}/documents/batch-upload`, form)
  },

  list: (spaceId: string, page = 0, size = 20, search?: string) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (search) params.set('search', search)
    return api.get<PageResult<DocumentResponse>>(
      `/spaces/${spaceId}/documents?${params}`
    )
  },

  get: (spaceId: string, docId: string) =>
    api.get<DocumentDetailResponse>(`/spaces/${spaceId}/documents/${docId}`),

  delete: (spaceId: string, docId: string) =>
    api.delete<void>(`/spaces/${spaceId}/documents/${docId}`),

  uploadVersion: (spaceId: string, docId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<DocumentResponse>(
      `/spaces/${spaceId}/documents/${docId}/versions`,
      form
    )
  },

  getVersions: (spaceId: string, docId: string) =>
    api.get<VersionResponse[]>(`/spaces/${spaceId}/documents/${docId}/versions`),

  retry: (spaceId: string, docId: string) =>
    api.post<DocumentResponse>(`/spaces/${spaceId}/documents/${docId}/retry`),

  batchUpdateTags: (
    spaceId: string,
    documentIds: string[],
    tagsToAdd: string[],
    tagsToRemove: string[]
  ) =>
    api.put<void>(`/spaces/${spaceId}/documents/batch-tags`, {
      documentIds,
      tagsToAdd,
      tagsToRemove,
    }),

  batchDelete: (spaceId: string, documentIds: string[]) =>
    api.delete<void>(`/spaces/${spaceId}/documents/batch-delete`, { documentIds }),
}
```

`rag-frontend/src/api/chat.ts`:
```typescript
import { api } from './client'
import type { SessionResponse, SessionDetailResponse } from './types'

export const chatApi = {
  createSession: (spaceId: string, title?: string) =>
    api.post<SessionResponse>(`/spaces/${spaceId}/sessions`, title ? { title } : {}),

  listSessions: (spaceId: string) =>
    api.get<SessionResponse[]>(`/spaces/${spaceId}/sessions`),

  getSession: (sessionId: string) =>
    api.get<SessionDetailResponse>(`/sessions/${sessionId}`),

  deleteSession: (sessionId: string) => api.delete<void>(`/sessions/${sessionId}`),
}
```

- [ ] **Step 4: Commit**

```bash
git add rag-frontend/src/api/
git commit -m "feat(frontend): add API client, type definitions, and domain API modules"
```

---

### Task 3: Zustand Stores

**Files:**
- Create: `rag-frontend/src/stores/useAuthStore.ts`
- Create: `rag-frontend/src/stores/useSpaceStore.ts`
- Create: `rag-frontend/src/stores/useChatStore.ts`
- Create: `rag-frontend/src/stores/useDocumentStore.ts`

- [ ] **Step 1: Create useAuthStore**

`rag-frontend/src/stores/useAuthStore.ts`:
```typescript
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserResponse } from '@/api/types'

interface AuthState {
  userId: string | null
  user: UserResponse | null
  setUserId: (id: string) => void
  setUser: (user: UserResponse) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      userId: null,
      user: null,
      setUserId: (id) => set({ userId: id }),
      setUser: (user) => set({ user }),
      logout: () => set({ userId: null, user: null }),
    }),
    { name: 'rag-auth' }
  )
)
```

- [ ] **Step 2: Create useSpaceStore**

`rag-frontend/src/stores/useSpaceStore.ts`:
```typescript
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { SpaceResponse } from '@/api/types'

interface SpaceState {
  spaces: SpaceResponse[]
  currentSpaceId: string | null
  setSpaces: (spaces: SpaceResponse[]) => void
  setCurrentSpaceId: (id: string) => void
  getCurrentSpace: () => SpaceResponse | undefined
}

export const useSpaceStore = create<SpaceState>()(
  persist(
    (set, get) => ({
      spaces: [],
      currentSpaceId: null,
      setSpaces: (spaces) => set({ spaces }),
      setCurrentSpaceId: (id) => set({ currentSpaceId: id }),
      getCurrentSpace: () => {
        const { spaces, currentSpaceId } = get()
        return spaces.find((s) => s.spaceId === currentSpaceId)
      },
    }),
    { name: 'rag-space' }
  )
)
```

- [ ] **Step 3: Create useChatStore**

`rag-frontend/src/stores/useChatStore.ts`:
```typescript
import { create } from 'zustand'
import type {
  SessionResponse,
  MessageResponse,
  CitationResponse,
} from '@/api/types'

interface ChatState {
  sessions: SessionResponse[]
  currentSessionId: string | null
  messages: MessageResponse[]
  // Streaming state
  isStreaming: boolean
  streamingContent: string
  streamingCitations: CitationResponse[]
  agentStatus: {
    round: number
    phase: 'thinking' | 'searching' | 'evaluating' | 'generating' | 'idle'
    queries: string[]
  }
  // Actions
  setSessions: (sessions: SessionResponse[]) => void
  setCurrentSessionId: (id: string | null) => void
  setMessages: (messages: MessageResponse[]) => void
  setStreaming: (streaming: boolean) => void
  appendStreamContent: (delta: string) => void
  addStreamCitation: (citation: CitationResponse) => void
  setAgentStatus: (status: Partial<ChatState['agentStatus']>) => void
  finishStreaming: (messageId: string) => void
  resetStream: () => void
}

export const useChatStore = create<ChatState>((set, get) => ({
  sessions: [],
  currentSessionId: null,
  messages: [],
  isStreaming: false,
  streamingContent: '',
  streamingCitations: [],
  agentStatus: { round: 0, phase: 'idle', queries: [] },

  setSessions: (sessions) => set({ sessions }),
  setCurrentSessionId: (id) => set({ currentSessionId: id }),
  setMessages: (messages) => set({ messages }),
  setStreaming: (streaming) => set({ isStreaming: streaming }),
  appendStreamContent: (delta) =>
    set((s) => ({ streamingContent: s.streamingContent + delta })),
  addStreamCitation: (citation) =>
    set((s) => ({ streamingCitations: [...s.streamingCitations, citation] })),
  setAgentStatus: (status) =>
    set((s) => ({ agentStatus: { ...s.agentStatus, ...status } })),
  finishStreaming: (messageId) => {
    const { streamingContent, streamingCitations, messages } = get()
    const assistantMsg: MessageResponse = {
      messageId,
      role: 'ASSISTANT',
      content: streamingContent,
      citations: streamingCitations,
      tokenCount: Math.floor(streamingContent.length / 3),
      createdAt: new Date().toISOString(),
    }
    set({
      messages: [...messages, assistantMsg],
      isStreaming: false,
      streamingContent: '',
      streamingCitations: [],
      agentStatus: { round: 0, phase: 'idle', queries: [] },
    })
  },
  resetStream: () =>
    set({
      isStreaming: false,
      streamingContent: '',
      streamingCitations: [],
      agentStatus: { round: 0, phase: 'idle', queries: [] },
    }),
}))
```

- [ ] **Step 4: Create useDocumentStore**

`rag-frontend/src/stores/useDocumentStore.ts`:
```typescript
import { create } from 'zustand'
import type { DocumentResponse } from '@/api/types'

interface DocumentState {
  documents: DocumentResponse[]
  totalElements: number
  totalPages: number
  currentPage: number
  searchQuery: string
  selectedIds: Set<string>
  // Actions
  setDocuments: (docs: DocumentResponse[], total: number, totalPages: number) => void
  setCurrentPage: (page: number) => void
  setSearchQuery: (query: string) => void
  toggleSelect: (id: string) => void
  selectAll: (ids: string[]) => void
  clearSelection: () => void
  updateDocumentStatus: (docId: string, status: string, chunkCount?: number) => void
}

export const useDocumentStore = create<DocumentState>((set, get) => ({
  documents: [],
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
  searchQuery: '',
  selectedIds: new Set(),

  setDocuments: (docs, total, totalPages) =>
    set({ documents: docs, totalElements: total, totalPages }),
  setCurrentPage: (page) => set({ currentPage: page }),
  setSearchQuery: (query) => set({ searchQuery: query, currentPage: 0 }),
  toggleSelect: (id) =>
    set((s) => {
      const next = new Set(s.selectedIds)
      next.has(id) ? next.delete(id) : next.add(id)
      return { selectedIds: next }
    }),
  selectAll: (ids) => set({ selectedIds: new Set(ids) }),
  clearSelection: () => set({ selectedIds: new Set() }),
  updateDocumentStatus: (docId, status, chunkCount) =>
    set((s) => ({
      documents: s.documents.map((d) =>
        d.documentId === docId
          ? { ...d, status: status as DocumentResponse['status'], ...(chunkCount !== undefined ? { chunkCount } : {}) }
          : d
      ),
    })),
}))
```

- [ ] **Step 5: Commit**

```bash
git add rag-frontend/src/stores/
git commit -m "feat(frontend): add Zustand stores - auth, space, chat, document"
```

---

### Task 4: SSE Hook + WebSocket Hook

**Files:**
- Create: `rag-frontend/src/hooks/useSSE.ts`
- Create: `rag-frontend/src/hooks/useDocumentNotification.ts`

- [ ] **Step 1: Create SSE streaming hook**

This hook connects to `POST /api/v1/sessions/{id}/chat`, parses the SSE event stream, and dispatches to useChatStore.

`rag-frontend/src/hooks/useSSE.ts`:
```typescript
import { useCallback, useRef } from 'react'
import { useAuthStore } from '@/stores/useAuthStore'
import { useChatStore } from '@/stores/useChatStore'
import type { MessageResponse } from '@/api/types'

export function useSSE() {
  const abortRef = useRef<AbortController | null>(null)

  const sendMessage = useCallback(async (sessionId: string, message: string) => {
    const userId = useAuthStore.getState().userId
    const store = useChatStore.getState()

    // Add user message to local state immediately
    const userMsg: MessageResponse = {
      messageId: crypto.randomUUID(),
      role: 'USER',
      content: message,
      citations: [],
      tokenCount: Math.floor(message.length / 3),
      createdAt: new Date().toISOString(),
    }
    store.setMessages([...store.messages, userMsg])
    store.setStreaming(true)
    store.resetStream()
    store.setStreaming(true)

    // Abort any previous stream
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller

    try {
      const res = await fetch(`/api/v1/sessions/${sessionId}/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-User-Id': userId || '',
        },
        body: JSON.stringify({ message }),
        signal: controller.signal,
      })

      if (!res.ok || !res.body) {
        store.resetStream()
        return
      }

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        let eventName = ''
        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:') && eventName) {
            const data = JSON.parse(line.slice(5).trim())
            handleEvent(eventName, data)
            eventName = ''
          }
        }
      }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      useChatStore.getState().resetStream()
    }
  }, [])

  const cancel = useCallback(() => {
    abortRef.current?.abort()
    useChatStore.getState().resetStream()
  }, [])

  return { sendMessage, cancel }
}

function handleEvent(eventName: string, data: Record<string, unknown>) {
  const store = useChatStore.getState()

  switch (eventName) {
    case 'agent_thinking':
      store.setAgentStatus({
        round: data.round as number,
        phase: 'thinking',
      })
      break
    case 'agent_searching':
      store.setAgentStatus({
        round: data.round as number,
        phase: 'searching',
        queries: data.queries as string[],
      })
      break
    case 'agent_evaluating':
      store.setAgentStatus({
        round: data.round as number,
        phase: 'evaluating',
      })
      if (data.sufficient) {
        store.setAgentStatus({ phase: 'generating' })
      }
      break
    case 'content_delta':
      if (store.agentStatus.phase !== 'generating') {
        store.setAgentStatus({ phase: 'generating' })
      }
      store.appendStreamContent(data.delta as string)
      break
    case 'citation':
      store.addStreamCitation({
        citationIndex: data.citationIndex as number,
        documentId: data.documentId as string,
        documentTitle: data.documentTitle as string,
        chunkId: data.chunkId as string,
        pageNumber: (data.pageNumber as number) || null,
        sectionPath: (data.sectionPath as string) || '',
        snippet: (data.snippet as string) || '',
      })
      break
    case 'done':
      store.finishStreaming(data.messageId as string)
      break
    case 'error':
      console.error('SSE error:', data.message)
      store.resetStream()
      break
  }
}
```

- [ ] **Step 2: Create WebSocket notification hook**

`rag-frontend/src/hooks/useDocumentNotification.ts`:
```typescript
import { useEffect, useRef } from 'react'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import { useDocumentStore } from '@/stores/useDocumentStore'

/**
 * Subscribes to document status changes via STOMP WebSocket.
 * Pass documentIds to subscribe to specific documents, or omit to skip.
 */
export function useDocumentNotification(documentIds: string[]) {
  const clientRef = useRef<Client | null>(null)
  const updateStatus = useDocumentStore((s) => s.updateDocumentStatus)

  useEffect(() => {
    if (documentIds.length === 0) return

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws/notifications'),
      reconnectDelay: 5000,
      onConnect: () => {
        documentIds.forEach((docId) => {
          client.subscribe(`/topic/documents/${docId}`, (msg) => {
            const event = JSON.parse(msg.body)
            if (event.type === 'DOCUMENT_STATUS_CHANGED') {
              const { documentId, status } = event.payload
              updateStatus(documentId, status)
            }
          })
        })
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
    }
  }, [documentIds.join(','), updateStatus])
}
```

- [ ] **Step 3: Commit**

```bash
git add rag-frontend/src/hooks/
git commit -m "feat(frontend): add SSE streaming hook and WebSocket notification hook"
```

---

### Task 5: Layout Components (Header, SpaceSelector, AppLayout)

**Files:**
- Create: `rag-frontend/src/components/layout/AppLayout.tsx`
- Create: `rag-frontend/src/components/layout/Header.tsx`
- Create: `rag-frontend/src/components/layout/SpaceSelector.tsx`
- Create: `rag-frontend/src/pages/LoginPage.tsx`

- [ ] **Step 1: Create LoginPage (mock)**

Since auth is not implemented, this is a simple page that lets users enter a userId to set `X-User-Id`.

`rag-frontend/src/pages/LoginPage.tsx`:
```typescript
import { useState } from 'react'
import { useAuthStore } from '@/stores/useAuthStore'
import { usersApi } from '@/api/users'

export function LoginPage() {
  const [inputId, setInputId] = useState('11111111-1111-1111-1111-111111111111')
  const [error, setError] = useState('')
  const { setUserId, setUser } = useAuthStore()

  const handleLogin = async () => {
    setError('')
    try {
      useAuthStore.getState().setUserId(inputId)
      const user = await usersApi.getMe()
      setUser(user)
      setUserId(inputId)
    } catch {
      setError('User not found. Make sure the user exists in the database.')
      useAuthStore.getState().logout()
    }
  }

  return (
    <div className="min-h-screen bg-bg-primary flex items-center justify-center">
      <div className="bg-bg-secondary rounded-lg p-8 w-96 space-y-6">
        <h1 className="font-heading text-h2 font-800 text-text-primary">
          RAG Knowledge Base
        </h1>
        <p className="text-text-secondary text-caption">
          Enter your User ID to continue (mock auth)
        </p>
        <input
          type="text"
          value={inputId}
          onChange={(e) => setInputId(e.target.value)}
          placeholder="User UUID"
          className="w-full bg-bg-tertiary text-text-primary rounded-md px-4 py-2
                     border border-citation-border focus:border-accent-blue
                     focus:outline-none font-mono text-code"
        />
        {error && <p className="text-status-failed text-caption">{error}</p>}
        <button
          onClick={handleLogin}
          className="w-full bg-accent-blue text-white rounded-md py-2
                     font-heading font-800 hover:opacity-90 transition-opacity"
        >
          Enter
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Create SpaceSelector**

`rag-frontend/src/components/layout/SpaceSelector.tsx`:
```typescript
import { useEffect } from 'react'
import { useSpaceStore } from '@/stores/useSpaceStore'
import { spacesApi } from '@/api/spaces'

export function SpaceSelector() {
  const { spaces, currentSpaceId, setSpaces, setCurrentSpaceId } = useSpaceStore()

  useEffect(() => {
    spacesApi.list().then((list) => {
      setSpaces(list)
      if (!currentSpaceId && list.length > 0) {
        setCurrentSpaceId(list[0].spaceId)
      }
    })
  }, [])

  return (
    <select
      value={currentSpaceId || ''}
      onChange={(e) => setCurrentSpaceId(e.target.value)}
      className="bg-bg-tertiary text-text-primary rounded-md px-3 py-1.5
                 border border-citation-border focus:border-accent-blue
                 focus:outline-none text-caption"
    >
      {spaces.map((s) => (
        <option key={s.spaceId} value={s.spaceId}>
          {s.name}
        </option>
      ))}
    </select>
  )
}
```

- [ ] **Step 3: Create Header**

`rag-frontend/src/components/layout/Header.tsx`:
```typescript
import { Link, useLocation } from 'react-router-dom'
import { SpaceSelector } from './SpaceSelector'
import { useAuthStore } from '@/stores/useAuthStore'
import { cn } from '@/lib/utils'

const navItems = [
  { path: '/chat', label: 'Chat' },
  { path: '/documents', label: 'Documents' },
  { path: '/spaces', label: 'Spaces' },
]

export function Header() {
  const location = useLocation()
  const { user, logout } = useAuthStore()

  return (
    <header className="h-14 bg-bg-secondary border-b border-citation-border
                        flex items-center justify-between px-6">
      <div className="flex items-center gap-6">
        <h1 className="font-heading font-900 text-body text-accent-purple">
          RAG KB
        </h1>
        <nav className="flex gap-1">
          {navItems.map((item) => (
            <Link
              key={item.path}
              to={item.path}
              className={cn(
                'px-3 py-1.5 rounded-md text-caption transition-colors',
                location.pathname === item.path
                  ? 'bg-bg-tertiary text-text-primary'
                  : 'text-text-secondary hover:text-text-primary'
              )}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </div>
      <div className="flex items-center gap-4">
        <SpaceSelector />
        <div className="flex items-center gap-2">
          <span className="text-caption text-text-secondary">
            {user?.displayName || user?.username}
          </span>
          <button
            onClick={logout}
            className="text-caption text-text-muted hover:text-text-secondary"
          >
            Logout
          </button>
        </div>
      </div>
    </header>
  )
}
```

- [ ] **Step 4: Create AppLayout**

`rag-frontend/src/components/layout/AppLayout.tsx`:
```typescript
import { Header } from './Header'

export function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="h-screen flex flex-col bg-bg-primary">
      <Header />
      <main className="flex-1 overflow-hidden">{children}</main>
    </div>
  )
}
```

- [ ] **Step 5: Commit**

```bash
git add rag-frontend/src/components/layout/ rag-frontend/src/pages/
git commit -m "feat(frontend): add layout components - Header, SpaceSelector, AppLayout, LoginPage"
```

---

### Task 6: Chat Page — Session List + Message Thread + Input

**Files:**
- Create: `rag-frontend/src/components/chat/ChatPage.tsx`
- Create: `rag-frontend/src/components/chat/SessionList.tsx`
- Create: `rag-frontend/src/components/chat/MessageThread.tsx`
- Create: `rag-frontend/src/components/chat/MessageBubble.tsx`
- Create: `rag-frontend/src/components/chat/AgentThinkingIndicator.tsx`
- Create: `rag-frontend/src/components/chat/StreamingText.tsx`
- Create: `rag-frontend/src/components/chat/CitationTag.tsx`
- Create: `rag-frontend/src/components/chat/CitationPanel.tsx`
- Create: `rag-frontend/src/components/chat/ChatInput.tsx`

- [ ] **Step 1: Create ChatPage (3-column layout)**

`rag-frontend/src/components/chat/ChatPage.tsx`:
```typescript
import { useEffect } from 'react'
import { useSpaceStore } from '@/stores/useSpaceStore'
import { useChatStore } from '@/stores/useChatStore'
import { chatApi } from '@/api/chat'
import { SessionList } from './SessionList'
import { MessageThread } from './MessageThread'
import { CitationPanel } from './CitationPanel'
import { ChatInput } from './ChatInput'

export function ChatPage() {
  const spaceId = useSpaceStore((s) => s.currentSpaceId)
  const { currentSessionId, setSessions, setMessages } = useChatStore()

  // Load sessions when space changes
  useEffect(() => {
    if (!spaceId) return
    chatApi.listSessions(spaceId).then(setSessions)
  }, [spaceId])

  // Load messages when session changes
  useEffect(() => {
    if (!currentSessionId) {
      setMessages([])
      return
    }
    chatApi.getSession(currentSessionId).then((detail) => {
      setMessages(detail.messages)
    })
  }, [currentSessionId])

  if (!spaceId) {
    return (
      <div className="h-full flex items-center justify-center text-text-muted">
        Select a knowledge space to start chatting
      </div>
    )
  }

  return (
    <div className="h-full flex">
      {/* Left: Session list */}
      <SessionList />

      {/* Center: Messages + Input */}
      <div className="flex-1 flex flex-col min-w-0">
        <MessageThread />
        <ChatInput />
      </div>

      {/* Right: Citation panel */}
      <CitationPanel />
    </div>
  )
}
```

- [ ] **Step 2: Create SessionList**

`rag-frontend/src/components/chat/SessionList.tsx`:
```typescript
import { useSpaceStore } from '@/stores/useSpaceStore'
import { useChatStore } from '@/stores/useChatStore'
import { chatApi } from '@/api/chat'
import { cn } from '@/lib/utils'

export function SessionList() {
  const spaceId = useSpaceStore((s) => s.currentSpaceId)
  const { sessions, currentSessionId, setCurrentSessionId, setSessions } =
    useChatStore()

  const handleNew = async () => {
    if (!spaceId) return
    const session = await chatApi.createSession(spaceId)
    setSessions([session, ...sessions])
    setCurrentSessionId(session.sessionId)
  }

  const handleDelete = async (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation()
    await chatApi.deleteSession(sessionId)
    setSessions(sessions.filter((s) => s.sessionId !== sessionId))
    if (currentSessionId === sessionId) setCurrentSessionId(null)
  }

  return (
    <div className="w-64 bg-bg-secondary border-r border-citation-border
                    flex flex-col">
      <div className="p-3">
        <button
          onClick={handleNew}
          className="w-full bg-accent-blue text-white rounded-md py-2
                     text-caption font-heading font-800
                     hover:opacity-90 transition-opacity"
        >
          + New Chat
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {sessions.map((s) => (
          <div
            key={s.sessionId}
            onClick={() => setCurrentSessionId(s.sessionId)}
            className={cn(
              'px-3 py-2.5 cursor-pointer border-b border-citation-border',
              'hover:bg-bg-tertiary transition-colors group',
              currentSessionId === s.sessionId && 'bg-bg-tertiary'
            )}
          >
            <div className="flex items-center justify-between">
              <span className="text-caption text-text-primary truncate flex-1">
                {s.title || 'New Chat'}
              </span>
              <button
                onClick={(e) => handleDelete(e, s.sessionId)}
                className="text-text-muted hover:text-status-failed
                           opacity-0 group-hover:opacity-100 text-caption ml-2"
              >
                ×
              </button>
            </div>
            <span className="text-[11px] text-text-muted">
              {new Date(s.lastActiveAt).toLocaleDateString()}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Create AgentThinkingIndicator**

`rag-frontend/src/components/chat/AgentThinkingIndicator.tsx`:
```typescript
import { useChatStore } from '@/stores/useChatStore'

const phaseLabels: Record<string, string> = {
  thinking: 'Analyzing query...',
  searching: 'Searching knowledge base...',
  evaluating: 'Evaluating results...',
  generating: 'Generating answer...',
}

export function AgentThinkingIndicator() {
  const { phase, round, queries } = useChatStore((s) => s.agentStatus)

  if (phase === 'idle') return null

  return (
    <div className="flex items-start gap-3 px-6 py-3">
      <div className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-accent-purple animate-pulse" />
          <span className="text-caption text-accent-purple">
            Round {round} — {phaseLabels[phase] || phase}
          </span>
        </div>
        {phase === 'searching' && queries.length > 0 && (
          <div className="ml-4 flex flex-wrap gap-1.5 mt-1">
            {queries.map((q, i) => (
              <span
                key={i}
                className="text-[11px] bg-citation-bg text-text-secondary
                           px-2 py-0.5 rounded-pill font-mono"
              >
                {q}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Create CitationTag (inline [1] link)**

`rag-frontend/src/components/chat/CitationTag.tsx`:
```typescript
interface CitationTagProps {
  index: number
  onClick: (index: number) => void
}

export function CitationTag({ index, onClick }: CitationTagProps) {
  return (
    <button
      onClick={() => onClick(index)}
      className="inline-flex items-center justify-center w-5 h-5
                 bg-accent-blue/20 text-accent-blue rounded text-[11px]
                 font-mono hover:bg-accent-blue/30 transition-colors
                 align-super mx-0.5"
    >
      {index}
    </button>
  )
}
```

- [ ] **Step 5: Create StreamingText**

`rag-frontend/src/components/chat/StreamingText.tsx`:
```typescript
import { useChatStore } from '@/stores/useChatStore'

export function StreamingText() {
  const content = useChatStore((s) => s.streamingContent)

  if (!content) return null

  return (
    <div className="px-6 py-3">
      <div className="bg-bg-tertiary rounded-lg px-4 py-3 max-w-[80%]">
        <p className="text-body text-text-primary whitespace-pre-wrap">
          {content}
          <span className="inline-block w-2 h-4 bg-accent-blue animate-pulse ml-0.5" />
        </p>
      </div>
    </div>
  )
}
```

- [ ] **Step 6: Create MessageBubble**

`rag-frontend/src/components/chat/MessageBubble.tsx`:
```typescript
import type { MessageResponse } from '@/api/types'
import { cn } from '@/lib/utils'

interface MessageBubbleProps {
  message: MessageResponse
  onCitationClick?: (index: number) => void
}

export function MessageBubble({ message, onCitationClick }: MessageBubbleProps) {
  const isUser = message.role === 'USER'

  // Replace [N] in content with clickable citation tags
  const renderContent = (text: string) => {
    const parts = text.split(/(\[\d+\])/)
    return parts.map((part, i) => {
      const match = part.match(/^\[(\d+)\]$/)
      if (match && onCitationClick) {
        const idx = parseInt(match[1])
        return (
          <button
            key={i}
            onClick={() => onCitationClick(idx)}
            className="inline-flex items-center justify-center w-5 h-5
                       bg-accent-blue/20 text-accent-blue rounded text-[11px]
                       font-mono hover:bg-accent-blue/30 transition-colors
                       align-super mx-0.5"
          >
            {idx}
          </button>
        )
      }
      return <span key={i}>{part}</span>
    })
  }

  return (
    <div className={cn('px-6 py-2', isUser && 'flex justify-end')}>
      <div
        className={cn(
          'rounded-lg px-4 py-3 max-w-[80%]',
          isUser
            ? 'bg-accent-blue/10 text-text-primary'
            : 'bg-bg-tertiary text-text-primary'
        )}
      >
        <p className="text-body whitespace-pre-wrap">
          {renderContent(message.content)}
        </p>
        {!isUser && message.citations.length > 0 && (
          <div className="mt-2 pt-2 border-t border-citation-border">
            <span className="text-[11px] text-text-muted">
              {message.citations.length} source(s) cited
            </span>
          </div>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 7: Create MessageThread**

`rag-frontend/src/components/chat/MessageThread.tsx`:
```typescript
import { useRef, useEffect } from 'react'
import { useChatStore } from '@/stores/useChatStore'
import { MessageBubble } from './MessageBubble'
import { AgentThinkingIndicator } from './AgentThinkingIndicator'
import { StreamingText } from './StreamingText'

export function MessageThread() {
  const { messages, isStreaming, currentSessionId } = useChatStore()
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, isStreaming])

  if (!currentSessionId) {
    return (
      <div className="flex-1 flex items-center justify-center text-text-muted">
        Select or create a chat session
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto py-4">
      {messages.map((msg) => (
        <MessageBubble key={msg.messageId} message={msg} />
      ))}
      {isStreaming && (
        <>
          <AgentThinkingIndicator />
          <StreamingText />
        </>
      )}
      <div ref={bottomRef} />
    </div>
  )
}
```

- [ ] **Step 8: Create CitationPanel**

`rag-frontend/src/components/chat/CitationPanel.tsx`:
```typescript
import { useChatStore } from '@/stores/useChatStore'
import type { CitationResponse } from '@/api/types'

export function CitationPanel() {
  const messages = useChatStore((s) => s.messages)
  const streamingCitations = useChatStore((s) => s.streamingCitations)

  // Collect all citations from the last assistant message + streaming
  const lastAssistant = [...messages].reverse().find((m) => m.role === 'ASSISTANT')
  const citations: CitationResponse[] = [
    ...(lastAssistant?.citations || []),
    ...streamingCitations,
  ]

  if (citations.length === 0) return null

  return (
    <div className="w-72 bg-bg-secondary border-l border-citation-border
                    overflow-y-auto">
      <div className="p-4">
        <h3 className="font-heading font-800 text-caption text-text-secondary mb-3">
          Sources
        </h3>
        <div className="space-y-2">
          {citations.map((c, i) => (
            <div
              key={`${c.documentId}-${c.citationIndex}-${i}`}
              className="bg-citation-bg border border-citation-border
                         rounded-md p-3 hover:bg-citation-hover transition-colors"
            >
              <div className="flex items-center gap-2 mb-1">
                <span className="inline-flex items-center justify-center w-5 h-5
                                 bg-accent-blue/20 text-accent-blue rounded
                                 text-[11px] font-mono">
                  {c.citationIndex}
                </span>
                <span className="text-caption text-text-primary font-heading font-800 truncate">
                  {c.documentTitle}
                </span>
              </div>
              {c.pageNumber && (
                <span className="text-[11px] text-text-muted">
                  Page {c.pageNumber}
                  {c.sectionPath && ` · ${c.sectionPath}`}
                </span>
              )}
              <p className="text-[11px] text-text-secondary mt-1.5 font-mono
                            line-clamp-3">
                {c.snippet}
              </p>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 9: Create ChatInput**

`rag-frontend/src/components/chat/ChatInput.tsx`:
```typescript
import { useState, useRef } from 'react'
import { useChatStore } from '@/stores/useChatStore'
import { useSSE } from '@/hooks/useSSE'

export function ChatInput() {
  const [input, setInput] = useState('')
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const { currentSessionId, isStreaming } = useChatStore()
  const { sendMessage } = useSSE()

  const handleSend = () => {
    const text = input.trim()
    if (!text || !currentSessionId || isStreaming) return
    setInput('')
    sendMessage(currentSessionId, text)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="border-t border-citation-border bg-bg-secondary p-4">
      <div className="flex gap-3 items-end max-w-4xl mx-auto">
        <textarea
          ref={inputRef}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={currentSessionId ? 'Ask a question...' : 'Select a session first'}
          disabled={!currentSessionId || isStreaming}
          rows={1}
          className="flex-1 bg-bg-tertiary text-text-primary rounded-lg px-4 py-3
                     border border-citation-border focus:border-accent-blue
                     focus:outline-none resize-none text-body
                     placeholder:text-text-muted disabled:opacity-50"
        />
        <button
          onClick={handleSend}
          disabled={!input.trim() || !currentSessionId || isStreaming}
          className="bg-accent-blue text-white rounded-lg px-5 py-3
                     font-heading font-800 text-caption
                     hover:opacity-90 transition-opacity
                     disabled:opacity-40 disabled:cursor-not-allowed"
        >
          {isStreaming ? '...' : 'Send'}
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 10: Commit**

```bash
git add rag-frontend/src/components/chat/
git commit -m "feat(frontend): add Chat page - SessionList, MessageThread, CitationPanel, SSE streaming"
```

---

### Task 7: Documents Page — Table + Upload + Status

**Files:**
- Create: `rag-frontend/src/components/documents/DocumentsPage.tsx`
- Create: `rag-frontend/src/components/documents/DocumentTable.tsx`
- Create: `rag-frontend/src/components/documents/StatusBadge.tsx`
- Create: `rag-frontend/src/components/documents/UploadDialog.tsx`
- Create: `rag-frontend/src/components/documents/DocumentDetailDialog.tsx`
- Create: `rag-frontend/src/components/documents/BatchTagDialog.tsx`

- [ ] **Step 1: Create StatusBadge**

`rag-frontend/src/components/documents/StatusBadge.tsx`:
```typescript
import { cn } from '@/lib/utils'

const statusConfig: Record<string, { label: string; color: string; icon: string }> = {
  UPLOADED: { label: 'Uploaded', color: 'text-accent-blue', icon: '○' },
  PARSING: { label: 'Parsing', color: 'text-status-parsing', icon: '◐' },
  PARSED: { label: 'Parsed', color: 'text-accent-purple', icon: '◑' },
  INDEXING: { label: 'Indexing', color: 'text-status-parsing', icon: '◕' },
  INDEXED: { label: 'Indexed', color: 'text-status-indexed', icon: '●' },
  FAILED: { label: 'Failed', color: 'text-status-failed', icon: '✕' },
}

export function StatusBadge({ status }: { status: string }) {
  const cfg = statusConfig[status] || statusConfig.UPLOADED

  return (
    <span className={cn('inline-flex items-center gap-1.5 text-caption', cfg.color)}>
      <span>{cfg.icon}</span>
      {cfg.label}
    </span>
  )
}
```

- [ ] **Step 2: Create DocumentTable**

`rag-frontend/src/components/documents/DocumentTable.tsx`:
```typescript
import { useDocumentStore } from '@/stores/useDocumentStore'
import { StatusBadge } from './StatusBadge'
import type { DocumentResponse } from '@/api/types'

interface DocumentTableProps {
  onView: (doc: DocumentResponse) => void
  onRetry: (doc: DocumentResponse) => void
  onDelete: (doc: DocumentResponse) => void
}

export function DocumentTable({ onView, onRetry, onDelete }: DocumentTableProps) {
  const { documents, selectedIds, toggleSelect, selectAll, clearSelection } =
    useDocumentStore()

  const allSelected = documents.length > 0 && selectedIds.size === documents.length

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-caption">
        <thead>
          <tr className="border-b border-citation-border text-text-muted text-left">
            <th className="p-3 w-10">
              <input
                type="checkbox"
                checked={allSelected}
                onChange={() =>
                  allSelected
                    ? clearSelection()
                    : selectAll(documents.map((d) => d.documentId))
                }
                className="accent-accent-blue"
              />
            </th>
            <th className="p-3">Name</th>
            <th className="p-3 w-20">Type</th>
            <th className="p-3 w-16">Ver</th>
            <th className="p-3 w-20">Level</th>
            <th className="p-3 w-24">Status</th>
            <th className="p-3 w-32">Tags</th>
            <th className="p-3 w-24">Actions</th>
          </tr>
        </thead>
        <tbody>
          {documents.map((doc) => (
            <tr
              key={doc.documentId}
              className="border-b border-citation-border hover:bg-bg-tertiary
                         transition-colors"
            >
              <td className="p-3">
                <input
                  type="checkbox"
                  checked={selectedIds.has(doc.documentId)}
                  onChange={() => toggleSelect(doc.documentId)}
                  className="accent-accent-blue"
                />
              </td>
              <td className="p-3 text-text-primary">{doc.title}</td>
              <td className="p-3 text-text-secondary">{doc.fileType}</td>
              <td className="p-3 text-text-secondary font-mono">{doc.currentVersionNo}</td>
              <td className="p-3">
                <span className={doc.securityLevel === 'MANAGEMENT'
                  ? 'text-accent-purple' : 'text-text-secondary'}>
                  {doc.securityLevel}
                </span>
              </td>
              <td className="p-3">
                <StatusBadge status={doc.status} />
              </td>
              <td className="p-3">
                <div className="flex gap-1 flex-wrap">
                  {doc.tags.slice(0, 2).map((tag) => (
                    <span key={tag} className="bg-citation-bg text-text-secondary
                                               px-1.5 py-0.5 rounded text-[11px]">
                      {tag}
                    </span>
                  ))}
                  {doc.tags.length > 2 && (
                    <span className="text-text-muted text-[11px]">
                      +{doc.tags.length - 2}
                    </span>
                  )}
                </div>
              </td>
              <td className="p-3">
                <div className="flex gap-2">
                  <button
                    onClick={() => onView(doc)}
                    className="text-accent-blue hover:underline text-[11px]"
                  >
                    View
                  </button>
                  {doc.status === 'FAILED' && (
                    <button
                      onClick={() => onRetry(doc)}
                      className="text-status-parsing hover:underline text-[11px]"
                    >
                      Retry
                    </button>
                  )}
                  <button
                    onClick={() => onDelete(doc)}
                    className="text-text-muted hover:text-status-failed text-[11px]"
                  >
                    Del
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
```

- [ ] **Step 3: Create UploadDialog**

`rag-frontend/src/components/documents/UploadDialog.tsx`:
```typescript
import { useState, useRef } from 'react'
import { documentsApi } from '@/api/documents'

interface UploadDialogProps {
  spaceId: string
  open: boolean
  onClose: () => void
  onUploaded: () => void
}

export function UploadDialog({ spaceId, open, onClose, onUploaded }: UploadDialogProps) {
  const [files, setFiles] = useState<File[]>([])
  const [uploading, setUploading] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  if (!open) return null

  const handleUpload = async () => {
    if (files.length === 0) return
    setUploading(true)
    try {
      if (files.length === 1) {
        await documentsApi.upload(spaceId, files[0])
      } else {
        await documentsApi.batchUpload(spaceId, files)
      }
      setFiles([])
      onUploaded()
      onClose()
    } catch (err) {
      console.error('Upload failed:', err)
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
         onClick={onClose}>
      <div className="bg-bg-secondary rounded-lg p-6 w-[480px] space-y-4"
           onClick={(e) => e.stopPropagation()}>
        <h2 className="font-heading font-800 text-h2 text-text-primary">
          Upload Documents
        </h2>
        <div
          className="border-2 border-dashed border-citation-border rounded-lg p-8
                     text-center cursor-pointer hover:border-accent-blue transition-colors"
          onClick={() => inputRef.current?.click()}
        >
          <p className="text-text-secondary text-caption">
            Click to select files (PDF, WORD, EXCEL)
          </p>
          <p className="text-text-muted text-[11px] mt-1">Max 100MB per file</p>
          <input
            ref={inputRef}
            type="file"
            multiple
            accept=".pdf,.doc,.docx,.xls,.xlsx"
            className="hidden"
            onChange={(e) => setFiles(Array.from(e.target.files || []))}
          />
        </div>
        {files.length > 0 && (
          <div className="space-y-1">
            {files.map((f, i) => (
              <div key={i} className="flex justify-between text-caption text-text-secondary">
                <span>{f.name}</span>
                <span className="text-text-muted">
                  {(f.size / 1024 / 1024).toFixed(1)} MB
                </span>
              </div>
            ))}
          </div>
        )}
        <div className="flex justify-end gap-3">
          <button onClick={onClose}
                  className="px-4 py-2 text-caption text-text-secondary
                             hover:text-text-primary">
            Cancel
          </button>
          <button
            onClick={handleUpload}
            disabled={files.length === 0 || uploading}
            className="bg-accent-blue text-white px-4 py-2 rounded-md
                       text-caption font-heading font-800
                       disabled:opacity-40"
          >
            {uploading ? 'Uploading...' : `Upload ${files.length} file(s)`}
          </button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Create DocumentDetailDialog**

`rag-frontend/src/components/documents/DocumentDetailDialog.tsx`:
```typescript
import { useEffect, useState } from 'react'
import { documentsApi } from '@/api/documents'
import { StatusBadge } from './StatusBadge'
import type { DocumentDetailResponse } from '@/api/types'

interface DocumentDetailDialogProps {
  spaceId: string
  documentId: string | null
  onClose: () => void
}

export function DocumentDetailDialog({ spaceId, documentId, onClose }: DocumentDetailDialogProps) {
  const [doc, setDoc] = useState<DocumentDetailResponse | null>(null)

  useEffect(() => {
    if (!documentId) return
    documentsApi.get(spaceId, documentId).then(setDoc)
  }, [documentId])

  if (!documentId) return null

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
         onClick={onClose}>
      <div className="bg-bg-secondary rounded-lg p-6 w-[560px] max-h-[80vh] overflow-y-auto"
           onClick={(e) => e.stopPropagation()}>
        {doc ? (
          <>
            <div className="flex justify-between items-start mb-4">
              <h2 className="font-heading font-800 text-h2 text-text-primary">
                {doc.title}
              </h2>
              <StatusBadge status={doc.status} />
            </div>
            <div className="grid grid-cols-2 gap-3 text-caption mb-4">
              <div>
                <span className="text-text-muted">Type:</span>{' '}
                <span className="text-text-secondary">{doc.fileType}</span>
              </div>
              <div>
                <span className="text-text-muted">Level:</span>{' '}
                <span className="text-text-secondary">{doc.securityLevel}</span>
              </div>
              <div>
                <span className="text-text-muted">Chunks:</span>{' '}
                <span className="text-text-secondary">{doc.chunkCount}</span>
              </div>
              <div>
                <span className="text-text-muted">Tags:</span>{' '}
                <span className="text-text-secondary">{doc.tags.join(', ') || '—'}</span>
              </div>
            </div>
            <h3 className="font-heading font-800 text-body text-text-primary mb-2">
              Version History
            </h3>
            <div className="space-y-2">
              {doc.versions.map((v) => (
                <div key={v.versionId}
                     className="bg-bg-tertiary rounded-md p-3 text-caption">
                  <div className="flex justify-between">
                    <span className="text-text-primary font-mono">v{v.versionNo}</span>
                    <span className="text-text-muted">
                      {(v.fileSize / 1024 / 1024).toFixed(1)} MB
                    </span>
                  </div>
                  <span className="text-[11px] text-text-muted">
                    {new Date(v.createdAt).toLocaleString()}
                  </span>
                </div>
              ))}
            </div>
          </>
        ) : (
          <p className="text-text-muted">Loading...</p>
        )}
        <div className="flex justify-end mt-4">
          <button onClick={onClose}
                  className="px-4 py-2 text-caption text-text-secondary
                             hover:text-text-primary">
            Close
          </button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Create BatchTagDialog**

`rag-frontend/src/components/documents/BatchTagDialog.tsx`:
```typescript
import { useState } from 'react'
import { documentsApi } from '@/api/documents'

interface BatchTagDialogProps {
  spaceId: string
  documentIds: string[]
  open: boolean
  onClose: () => void
  onDone: () => void
}

export function BatchTagDialog({ spaceId, documentIds, open, onClose, onDone }: BatchTagDialogProps) {
  const [tagsToAdd, setTagsToAdd] = useState('')
  const [tagsToRemove, setTagsToRemove] = useState('')

  if (!open) return null

  const handleSubmit = async () => {
    const add = tagsToAdd.split(',').map((t) => t.trim()).filter(Boolean)
    const remove = tagsToRemove.split(',').map((t) => t.trim()).filter(Boolean)
    await documentsApi.batchUpdateTags(spaceId, documentIds, add, remove)
    onDone()
    onClose()
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
         onClick={onClose}>
      <div className="bg-bg-secondary rounded-lg p-6 w-96 space-y-4"
           onClick={(e) => e.stopPropagation()}>
        <h2 className="font-heading font-800 text-h2 text-text-primary">
          Batch Update Tags
        </h2>
        <p className="text-caption text-text-secondary">
          Updating {documentIds.length} document(s)
        </p>
        <div>
          <label className="text-caption text-text-muted">Tags to add (comma-separated)</label>
          <input value={tagsToAdd} onChange={(e) => setTagsToAdd(e.target.value)}
                 className="w-full bg-bg-tertiary text-text-primary rounded-md px-3 py-2
                            border border-citation-border mt-1 text-caption" />
        </div>
        <div>
          <label className="text-caption text-text-muted">Tags to remove (comma-separated)</label>
          <input value={tagsToRemove} onChange={(e) => setTagsToRemove(e.target.value)}
                 className="w-full bg-bg-tertiary text-text-primary rounded-md px-3 py-2
                            border border-citation-border mt-1 text-caption" />
        </div>
        <div className="flex justify-end gap-3">
          <button onClick={onClose}
                  className="px-4 py-2 text-caption text-text-secondary">Cancel</button>
          <button onClick={handleSubmit}
                  className="bg-accent-blue text-white px-4 py-2 rounded-md
                             text-caption font-heading font-800">Apply</button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 6: Create DocumentsPage**

`rag-frontend/src/components/documents/DocumentsPage.tsx`:
```typescript
import { useEffect, useState, useCallback } from 'react'
import { useSpaceStore } from '@/stores/useSpaceStore'
import { useDocumentStore } from '@/stores/useDocumentStore'
import { documentsApi } from '@/api/documents'
import { DocumentTable } from './DocumentTable'
import { UploadDialog } from './UploadDialog'
import { DocumentDetailDialog } from './DocumentDetailDialog'
import { BatchTagDialog } from './BatchTagDialog'
import type { DocumentResponse } from '@/api/types'

export function DocumentsPage() {
  const spaceId = useSpaceStore((s) => s.currentSpaceId)
  const {
    documents, totalElements, totalPages, currentPage, searchQuery, selectedIds,
    setDocuments, setCurrentPage, setSearchQuery, clearSelection,
  } = useDocumentStore()

  const [uploadOpen, setUploadOpen] = useState(false)
  const [detailDocId, setDetailDocId] = useState<string | null>(null)
  const [tagDialogOpen, setTagDialogOpen] = useState(false)

  const loadDocuments = useCallback(() => {
    if (!spaceId) return
    documentsApi.list(spaceId, currentPage, 20, searchQuery || undefined).then((page) => {
      setDocuments(page.content, page.totalElements, page.totalPages)
    })
  }, [spaceId, currentPage, searchQuery])

  useEffect(() => { loadDocuments() }, [loadDocuments])

  const handleRetry = async (doc: DocumentResponse) => {
    if (!spaceId) return
    await documentsApi.retry(spaceId, doc.documentId)
    loadDocuments()
  }

  const handleDelete = async (doc: DocumentResponse) => {
    if (!spaceId) return
    await documentsApi.delete(spaceId, doc.documentId)
    loadDocuments()
  }

  const handleBatchDelete = async () => {
    if (!spaceId || selectedIds.size === 0) return
    await documentsApi.batchDelete(spaceId, Array.from(selectedIds))
    clearSelection()
    loadDocuments()
  }

  if (!spaceId) {
    return (
      <div className="h-full flex items-center justify-center text-text-muted">
        Select a knowledge space
      </div>
    )
  }

  // Count stats
  const indexed = documents.filter((d) => d.status === 'INDEXED').length
  const parsing = documents.filter((d) => ['PARSING', 'INDEXING'].includes(d.status)).length
  const failed = documents.filter((d) => d.status === 'FAILED').length

  return (
    <div className="h-full flex flex-col">
      {/* Toolbar */}
      <div className="flex items-center justify-between p-4 border-b border-citation-border">
        <div className="flex gap-2">
          <button
            onClick={() => setUploadOpen(true)}
            className="bg-accent-blue text-white px-4 py-2 rounded-md
                       text-caption font-heading font-800 hover:opacity-90"
          >
            Upload
          </button>
          {selectedIds.size > 0 && (
            <>
              <button
                onClick={() => setTagDialogOpen(true)}
                className="bg-bg-tertiary text-text-primary px-3 py-2 rounded-md
                           text-caption border border-citation-border"
              >
                Batch Tag ({selectedIds.size})
              </button>
              <button
                onClick={handleBatchDelete}
                className="bg-bg-tertiary text-status-failed px-3 py-2 rounded-md
                           text-caption border border-citation-border"
              >
                Delete ({selectedIds.size})
              </button>
            </>
          )}
        </div>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search documents..."
          className="bg-bg-tertiary text-text-primary rounded-md px-3 py-2
                     border border-citation-border w-64 text-caption
                     focus:border-accent-blue focus:outline-none"
        />
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto">
        <DocumentTable
          onView={(doc) => setDetailDocId(doc.documentId)}
          onRetry={handleRetry}
          onDelete={handleDelete}
        />
      </div>

      {/* Footer stats + pagination */}
      <div className="flex items-center justify-between px-4 py-3
                      border-t border-citation-border text-caption text-text-muted">
        <span>
          {totalElements} documents | Indexed: {indexed} | Processing: {parsing} | Failed: {failed}
        </span>
        <div className="flex gap-2">
          <button
            disabled={currentPage === 0}
            onClick={() => setCurrentPage(currentPage - 1)}
            className="px-3 py-1 rounded border border-citation-border
                       disabled:opacity-30 hover:bg-bg-tertiary"
          >
            Prev
          </button>
          <span className="px-2 py-1">
            {currentPage + 1} / {Math.max(totalPages, 1)}
          </span>
          <button
            disabled={currentPage >= totalPages - 1}
            onClick={() => setCurrentPage(currentPage + 1)}
            className="px-3 py-1 rounded border border-citation-border
                       disabled:opacity-30 hover:bg-bg-tertiary"
          >
            Next
          </button>
        </div>
      </div>

      {/* Dialogs */}
      <UploadDialog spaceId={spaceId} open={uploadOpen}
                    onClose={() => setUploadOpen(false)} onUploaded={loadDocuments} />
      <DocumentDetailDialog spaceId={spaceId} documentId={detailDocId}
                            onClose={() => setDetailDocId(null)} />
      <BatchTagDialog spaceId={spaceId} documentIds={Array.from(selectedIds)}
                      open={tagDialogOpen} onClose={() => setTagDialogOpen(false)}
                      onDone={loadDocuments} />
    </div>
  )
}
```

- [ ] **Step 7: Commit**

```bash
git add rag-frontend/src/components/documents/
git commit -m "feat(frontend): add Documents page - table, upload, detail, batch tags, status badges"
```

---

### Task 8: Spaces Page

**Files:**
- Create: `rag-frontend/src/components/spaces/SpacesPage.tsx`
- Create: `rag-frontend/src/components/spaces/CreateSpaceDialog.tsx`
- Create: `rag-frontend/src/components/spaces/AccessRuleEditor.tsx`

- [ ] **Step 1: Create CreateSpaceDialog**

`rag-frontend/src/components/spaces/CreateSpaceDialog.tsx`:
```typescript
import { useState } from 'react'
import { spacesApi } from '@/api/spaces'

interface CreateSpaceDialogProps {
  open: boolean
  onClose: () => void
  onCreated: () => void
}

export function CreateSpaceDialog({ open, onClose, onCreated }: CreateSpaceDialogProps) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [ownerTeam, setOwnerTeam] = useState('')
  const [language, setLanguage] = useState('zh')
  const [indexName, setIndexName] = useState('')

  if (!open) return null

  const handleCreate = async () => {
    await spacesApi.create({ name, description, ownerTeam, language, indexName })
    setName(''); setDescription(''); setOwnerTeam(''); setIndexName('')
    onCreated()
    onClose()
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
         onClick={onClose}>
      <div className="bg-bg-secondary rounded-lg p-6 w-[480px] space-y-4"
           onClick={(e) => e.stopPropagation()}>
        <h2 className="font-heading font-800 text-h2 text-text-primary">
          Create Knowledge Space
        </h2>
        {[
          { label: 'Name', value: name, set: setName, required: true },
          { label: 'Description', value: description, set: setDescription },
          { label: 'Owner Team', value: ownerTeam, set: setOwnerTeam, required: true },
          { label: 'Index Name', value: indexName, set: setIndexName, required: true,
            placeholder: 'e.g. kb_compliance_v1' },
        ].map((field) => (
          <div key={field.label}>
            <label className="text-caption text-text-muted">
              {field.label} {field.required && '*'}
            </label>
            <input
              value={field.value}
              onChange={(e) => field.set(e.target.value)}
              placeholder={field.placeholder}
              className="w-full bg-bg-tertiary text-text-primary rounded-md px-3 py-2
                         border border-citation-border mt-1 text-caption"
            />
          </div>
        ))}
        <div>
          <label className="text-caption text-text-muted">Language *</label>
          <select value={language} onChange={(e) => setLanguage(e.target.value)}
                  className="w-full bg-bg-tertiary text-text-primary rounded-md px-3 py-2
                             border border-citation-border mt-1 text-caption">
            <option value="zh">Chinese</option>
            <option value="en">English</option>
          </select>
        </div>
        <div className="flex justify-end gap-3">
          <button onClick={onClose}
                  className="px-4 py-2 text-caption text-text-secondary">Cancel</button>
          <button
            onClick={handleCreate}
            disabled={!name || !ownerTeam || !indexName}
            className="bg-accent-blue text-white px-4 py-2 rounded-md
                       text-caption font-heading font-800 disabled:opacity-40"
          >
            Create
          </button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Create AccessRuleEditor**

`rag-frontend/src/components/spaces/AccessRuleEditor.tsx`:
```typescript
import { useState } from 'react'
import { spacesApi } from '@/api/spaces'
import type { AccessRuleInput, AccessRuleResponse } from '@/api/types'

interface AccessRuleEditorProps {
  spaceId: string
  rules: AccessRuleResponse[]
  onUpdated: () => void
}

export function AccessRuleEditor({ spaceId, rules, onUpdated }: AccessRuleEditorProps) {
  const [newRules, setNewRules] = useState<AccessRuleInput[]>(
    rules.map((r) => ({
      targetType: r.targetType,
      targetValue: r.targetValue,
      docSecurityClearance: r.docSecurityClearance,
    }))
  )

  const addRule = () =>
    setNewRules([...newRules, { targetType: 'TEAM', targetValue: '', docSecurityClearance: 'ALL' }])

  const removeRule = (i: number) =>
    setNewRules(newRules.filter((_, idx) => idx !== i))

  const updateRule = (i: number, field: string, value: string) =>
    setNewRules(newRules.map((r, idx) => (idx === i ? { ...r, [field]: value } : r)))

  const save = async () => {
    await spacesApi.updateAccessRules(spaceId, newRules)
    onUpdated()
  }

  return (
    <div className="space-y-3">
      <h4 className="text-caption text-text-muted">Access Rules</h4>
      {newRules.map((rule, i) => (
        <div key={i} className="flex gap-2 items-center">
          <select
            value={rule.targetType}
            onChange={(e) => updateRule(i, 'targetType', e.target.value)}
            className="bg-bg-tertiary text-text-primary rounded px-2 py-1
                       text-caption border border-citation-border"
          >
            <option value="BU">BU</option>
            <option value="TEAM">TEAM</option>
            <option value="USER">USER</option>
          </select>
          <input
            value={rule.targetValue}
            onChange={(e) => updateRule(i, 'targetValue', e.target.value)}
            placeholder="Value"
            className="flex-1 bg-bg-tertiary text-text-primary rounded px-2 py-1
                       text-caption border border-citation-border"
          />
          <select
            value={rule.docSecurityClearance || 'ALL'}
            onChange={(e) => updateRule(i, 'docSecurityClearance', e.target.value)}
            className="bg-bg-tertiary text-text-primary rounded px-2 py-1
                       text-caption border border-citation-border"
          >
            <option value="ALL">ALL</option>
            <option value="MANAGEMENT">MANAGEMENT</option>
          </select>
          <button onClick={() => removeRule(i)}
                  className="text-status-failed text-caption">×</button>
        </div>
      ))}
      <div className="flex gap-2">
        <button onClick={addRule}
                className="text-accent-blue text-caption hover:underline">+ Add Rule</button>
        <button onClick={save}
                className="bg-accent-blue text-white px-3 py-1 rounded text-caption
                           font-heading font-800">Save</button>
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Create SpacesPage**

`rag-frontend/src/components/spaces/SpacesPage.tsx`:
```typescript
import { useEffect, useState } from 'react'
import { useSpaceStore } from '@/stores/useSpaceStore'
import { spacesApi } from '@/api/spaces'
import { CreateSpaceDialog } from './CreateSpaceDialog'
import { AccessRuleEditor } from './AccessRuleEditor'
import type { SpaceResponse } from '@/api/types'

export function SpacesPage() {
  const { spaces, setSpaces } = useSpaceStore()
  const [createOpen, setCreateOpen] = useState(false)
  const [expandedId, setExpandedId] = useState<string | null>(null)

  const loadSpaces = () => {
    spacesApi.list().then(setSpaces)
  }

  useEffect(() => { loadSpaces() }, [])

  return (
    <div className="h-full overflow-y-auto p-6 max-w-4xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <h1 className="font-heading font-900 text-h1 text-text-primary">
          Knowledge Spaces
        </h1>
        <button
          onClick={() => setCreateOpen(true)}
          className="bg-accent-blue text-white px-4 py-2 rounded-md
                     text-caption font-heading font-800 hover:opacity-90"
        >
          + Create Space
        </button>
      </div>

      <div className="space-y-3">
        {spaces.map((space) => (
          <div key={space.spaceId}
               className="bg-bg-secondary border border-citation-border rounded-lg p-4">
            <div className="flex justify-between items-start cursor-pointer"
                 onClick={() => setExpandedId(
                   expandedId === space.spaceId ? null : space.spaceId
                 )}>
              <div>
                <h3 className="font-heading font-800 text-body text-text-primary">
                  {space.name}
                </h3>
                <p className="text-caption text-text-secondary mt-0.5">
                  {space.description || 'No description'}
                </p>
                <div className="flex gap-4 mt-2 text-[11px] text-text-muted">
                  <span>Team: {space.ownerTeam}</span>
                  <span>Lang: {space.language}</span>
                  <span>Index: <code className="font-mono">{space.indexName}</code></span>
                  <span>Rules: {space.accessRules.length}</span>
                </div>
              </div>
              <span className="text-text-muted text-caption">
                {expandedId === space.spaceId ? '▲' : '▼'}
              </span>
            </div>
            {expandedId === space.spaceId && (
              <div className="mt-4 pt-4 border-t border-citation-border">
                <AccessRuleEditor
                  spaceId={space.spaceId}
                  rules={space.accessRules}
                  onUpdated={loadSpaces}
                />
              </div>
            )}
          </div>
        ))}
      </div>

      <CreateSpaceDialog open={createOpen}
                         onClose={() => setCreateOpen(false)} onCreated={loadSpaces} />
    </div>
  )
}
```

- [ ] **Step 4: Commit**

```bash
git add rag-frontend/src/components/spaces/
git commit -m "feat(frontend): add Spaces page - create, list, access rule editor"
```

---

### Task 9: Font Files + Final Build + CLAUDE.md

- [ ] **Step 1: Download font files**

```bash
cd "E:/AI Application/Agentic_RAG/rag-frontend"
mkdir -p public/fonts
# Download from Google Fonts / JetBrains CDN
# Bricolage Grotesque 800, 900
curl -L "https://fonts.gstatic.com/s/bricolagegrotesque/v1/3y9U6as8bTXq_nANBjzKo3IQZYI.woff2" -o public/fonts/bricolage-grotesque-800.woff2
curl -L "https://fonts.gstatic.com/s/bricolagegrotesque/v1/3y9U6as8bTXq_nANBjzKo3IQfYI.woff2" -o public/fonts/bricolage-grotesque-900.woff2
# IBM Plex Sans 200, 400
curl -L "https://fonts.gstatic.com/s/ibmplexsans/v19/zYX9KVElMYYaJe8bpLHnCwDKjR76AI9sdQ.woff2" -o public/fonts/ibm-plex-sans-200.woff2
curl -L "https://fonts.gstatic.com/s/ibmplexsans/v19/zYXgKVElMYYaJe8bpLHnCwDKhdHeFQ.woff2" -o public/fonts/ibm-plex-sans-400.woff2
# JetBrains Mono 400
curl -L "https://fonts.gstatic.com/s/jetbrainsmono/v18/tDbY2o-flEEny0FZhsfKu5WU4zr3E_BX0PnT8RD8yKxTOlOV.woff2" -o public/fonts/jetbrains-mono-400.woff2
```

Note: If these URLs change, download manually from Google Fonts and JetBrains. The plan proceeds even if fonts are placeholders — the system font fallback works.

- [ ] **Step 2: Add .gitignore for frontend**

`rag-frontend/.gitignore`:
```
node_modules
dist
.env
.env.local
```

- [ ] **Step 3: Verify build**

```bash
cd "E:/AI Application/Agentic_RAG/rag-frontend"
npm install && npx tsc --noEmit && echo "TYPE CHECK OK"
```

If type errors exist, fix them iteratively. Then:

```bash
npm run build && echo "BUILD OK"
```

- [ ] **Step 4: Update CLAUDE.md**

In `CLAUDE.md`, update Implementation Status:
```markdown
- [x] Plan 5: React Frontend
```

Add to Commands section:
```markdown
# Frontend
cd rag-frontend && npm install && npm run dev  # Start dev server on :3000 (proxies /api to :8080)
cd rag-frontend && npm run build               # Production build to dist/
```

- [ ] **Step 5: Final commit**

```bash
git add rag-frontend/ CLAUDE.md
git commit -m "feat(frontend): complete React frontend - Chat, Documents, Spaces pages with SSE streaming"
```

---

## Dependency Graph

```
Task 1 (Scaffolding)
  └──► Task 2 (API Client + Types)
        └──► Task 3 (Zustand Stores)
              └──► Task 4 (SSE + WebSocket Hooks)
                    ├──► Task 5 (Layout Components)
                    │     └──► Task 6 (Chat Page)
                    ├──► Task 7 (Documents Page)
                    └──► Task 8 (Spaces Page)
                          └──► Task 9 (Fonts + Build + CLAUDE.md)
```

## Backend API ↔ Frontend Mapping

| Frontend Component | Backend Endpoint | Method |
|---|---|---|
| LoginPage | `GET /api/v1/users/me` | Verify user exists |
| SpaceSelector | `GET /api/v1/spaces` | List accessible spaces |
| SpacesPage | `POST/GET /api/v1/spaces`, `PUT /spaces/{id}/access-rules` | CRUD |
| SessionList | `GET/POST /api/v1/spaces/{id}/sessions`, `DELETE /sessions/{id}` | Session CRUD |
| ChatInput → useSSE | `POST /api/v1/sessions/{id}/chat` (SSE stream) | Agent Q&A |
| MessageThread | SSE events: `agent_thinking`, `content_delta`, `citation`, `done` | Streaming |
| CitationPanel | SSE `citation` events + `MessageResponse.citations` | Source display |
| DocumentsPage | `GET/DELETE /spaces/{id}/documents/*`, batch ops | Doc management |
| UploadDialog | `POST /spaces/{id}/documents/upload`, `POST .../batch-upload` | File upload |
| DocumentDetailDialog | `GET /spaces/{id}/documents/{id}` | Detail + versions |
| useDocumentNotification | `ws://host/ws/notifications` → `/topic/documents/{id}` | Status updates |

## Key Design Decisions

| Decision | Reason |
|---|---|
| Native `fetch` + `ReadableStream` for SSE (not EventSource) | `EventSource` only supports GET; chat needs POST with JSON body |
| Zustand over Redux/Context | Minimal boilerplate, works outside React components (SSE handler) |
| Vite proxy for `/api` and `/ws` | Avoids CORS in dev; production uses nginx reverse proxy |
| No shadcn/ui primitives in initial plan | Keep bundle small; can add via `npx shadcn-ui@latest add button` later |
| Design tokens in Tailwind config, not CSS variables | Simpler, type-safe with Tailwind IntelliSense |
| Mock LoginPage instead of real auth | Backend uses `X-User-Id` header, no auth system yet |
