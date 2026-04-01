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
