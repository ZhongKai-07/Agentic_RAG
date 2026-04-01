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
