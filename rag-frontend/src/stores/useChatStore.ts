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
