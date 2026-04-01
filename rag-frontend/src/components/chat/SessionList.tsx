import { useSpaceStore } from '@/stores/useSpaceStore'
import { useChatStore } from '@/stores/useChatStore'
import { chatApi } from '@/api/chat'
import { cn } from '@/lib/utils'

export function SessionList() {
  const spaceId = useSpaceStore((s) => s.currentSpaceId)
  const { sessions, currentSessionId, setCurrentSessionId, setSessions } =
    useChatStore()

  const handleNew = async () => {
    if (!spaceId) return
    const session = await chatApi.createSession(spaceId)
    setSessions([session, ...sessions])
    setCurrentSessionId(session.sessionId)
  }

  const handleDelete = async (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation()
    await chatApi.deleteSession(sessionId)
    setSessions(sessions.filter((s) => s.sessionId !== sessionId))
    if (currentSessionId === sessionId) setCurrentSessionId(null)
  }

  return (
    <div className="w-64 bg-white border-r-2 border-dashed border-text-primary
                    flex flex-col">
      <div className="p-3">
        <button
          onClick={handleNew}
          className="w-full bg-accent-blue text-white btn-hand py-2
                     text-caption font-heading font-800"
        >
          + New Chat
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {sessions.map((s) => (
          <div
            key={s.sessionId}
            onClick={() => setCurrentSessionId(s.sessionId)}
            className={cn(
              'px-3 py-2.5 cursor-pointer border-b border-dashed',
              'hover:bg-bg-tertiary hover:rotate-[0.5deg] transition-transform duration-150 group',
              currentSessionId === s.sessionId && 'bg-bg-tertiary border-l-[3px] border-l-accent-blue'
            )}
          >
            <div className="flex items-center justify-between">
              <span className="text-caption text-text-primary truncate flex-1">
                {s.title || 'New Chat'}
              </span>
              <button
                onClick={(e) => handleDelete(e, s.sessionId)}
                className="text-text-muted hover:text-status-failed
                           opacity-0 group-hover:opacity-100 text-caption ml-2"
              >
                ×
              </button>
            </div>
            <span className="text-[11px] text-text-muted">
              {new Date(s.lastActiveAt).toLocaleDateString()}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
