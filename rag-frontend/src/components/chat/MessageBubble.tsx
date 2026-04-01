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
