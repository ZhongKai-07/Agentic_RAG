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
