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
