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
