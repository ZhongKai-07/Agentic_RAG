import { useChatStore } from '@/stores/useChatStore'

const phaseLabels: Record<string, string> = {
  thinking: 'Analyzing query...',
  searching: 'Searching knowledge base...',
  evaluating: 'Evaluating results...',
  generating: 'Generating answer...',
}

export function AgentThinkingIndicator() {
  const { phase, round, queries } = useChatStore((s) => s.agentStatus)

  if (phase === 'idle') return null

  return (
    <div className="flex items-start gap-3 px-6 py-3">
      <div className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-accent-purple animate-jiggle" />
          <span className="text-caption text-accent-purple">
            Round {round} — {phaseLabels[phase] || phase}
          </span>
        </div>
        {phase === 'searching' && queries.length > 0 && (
          <div className="ml-4 flex flex-wrap gap-1.5 mt-1">
            {queries.map((q, i) => (
              <span
                key={i}
                className="text-[11px] bg-citation-bg text-text-secondary
                           px-2 py-0.5 wobbly-border border border-text-primary"
              >
                {q}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
