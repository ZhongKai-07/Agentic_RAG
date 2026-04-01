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
  items: T[]
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
