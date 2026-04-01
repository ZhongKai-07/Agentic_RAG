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
