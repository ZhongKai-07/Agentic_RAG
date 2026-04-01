import { useChatStore } from '@/stores/useChatStore'
import type { CitationResponse } from '@/api/types'

export function CitationPanel() {
  const messages = useChatStore((s) => s.messages)
  const streamingCitations = useChatStore((s) => s.streamingCitations)

  // Collect all citations from the last assistant message + streaming
  const lastAssistant = [...messages].reverse().find((m) => m.role === 'ASSISTANT')
  const citations: CitationResponse[] = [
    ...(lastAssistant?.citations || []),
    ...streamingCitations,
  ]

  if (citations.length === 0) return null

  return (
    <div className="w-72 bg-bg-secondary border-l border-citation-border
                    overflow-y-auto">
      <div className="p-4">
        <h3 className="font-heading font-800 text-caption text-text-secondary mb-3">
          Sources
        </h3>
        <div className="space-y-2">
          {citations.map((c, i) => (
            <div
              key={`${c.documentId}-${c.citationIndex}-${i}`}
              className="bg-citation-bg border border-citation-border
                         rounded-md p-3 hover:bg-citation-hover transition-colors"
            >
              <div className="flex items-center gap-2 mb-1">
                <span className="inline-flex items-center justify-center w-5 h-5
                                 bg-accent-blue/20 text-accent-blue rounded
                                 text-[11px] font-mono">
                  {c.citationIndex}
                </span>
                <span className="text-caption text-text-primary font-heading font-800 truncate">
                  {c.documentTitle}
                </span>
              </div>
              {c.pageNumber && (
                <span className="text-[11px] text-text-muted">
                  Page {c.pageNumber}
                  {c.sectionPath && ` · ${c.sectionPath}`}
                </span>
              )}
              <p className="text-[11px] text-text-secondary mt-1.5 font-mono
                            line-clamp-3">
                {c.snippet}
              </p>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
