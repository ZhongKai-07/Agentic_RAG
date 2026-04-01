import { cn } from '@/lib/utils'

const statusConfig: Record<string, { label: string; color: string; icon: string }> = {
  UPLOADED: { label: 'Uploaded', color: 'text-accent-blue', icon: '○' },
  PARSING: { label: 'Parsing', color: 'text-status-parsing', icon: '◐' },
  PARSED: { label: 'Parsed', color: 'text-accent-purple', icon: '◑' },
  INDEXING: { label: 'Indexing', color: 'text-status-parsing', icon: '◕' },
  INDEXED: { label: 'Indexed', color: 'text-status-indexed', icon: '●' },
  FAILED: { label: 'Failed', color: 'text-status-failed', icon: '✕' },
}

export function StatusBadge({ status }: { status: string }) {
  const cfg = statusConfig[status] || statusConfig.UPLOADED

  return (
    <span className={cn('inline-flex items-center gap-1.5 text-caption bg-white border border-text-primary wobbly-border px-2 py-0.5', cfg.color)}>
      <span>{cfg.icon}</span>
      {cfg.label}
    </span>
  )
}
