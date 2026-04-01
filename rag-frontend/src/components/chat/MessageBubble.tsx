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
                       bg-accent-blue/20 text-accent-blue wobbly-border text-[11px]
                       border border-text-primary hover:bg-accent-blue/30 hover:rotate-3 transition-transform
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
          'wobbly-border px-4 py-3 max-w-[80%] border-2 border-text-primary shadow-hard-sm',
          isUser
            ? 'bg-[#fff9b1] text-text-primary'
            : 'bg-white text-text-primary'
        )}
      >
        <p className="text-body whitespace-pre-wrap">
          {renderContent(message.content)}
        </p>
        {!isUser && message.citations.length > 0 && (
          <div className="mt-2 pt-2 border-t-2 border-dashed">
            <span className="text-[11px] text-text-muted">
              {message.citations.length} source(s) cited
            </span>
          </div>
        )}
      </div>
    </div>
  )
}
