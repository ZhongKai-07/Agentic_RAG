import { useEffect, useState } from 'react'
import { useSpaceStore } from '@/stores/useSpaceStore'
import { spacesApi } from '@/api/spaces'
import { CreateSpaceDialog } from './CreateSpaceDialog'
import { AccessRuleEditor } from './AccessRuleEditor'

export function SpacesPage() {
  const { spaces, setSpaces } = useSpaceStore()
  const [createOpen, setCreateOpen] = useState(false)
  const [expandedId, setExpandedId] = useState<string | null>(null)

  const loadSpaces = () => {
    spacesApi.list()
      .then((list) => { console.log('[SpacesPage] loaded spaces:', list); setSpaces(list) })
      .catch((err) => console.error('[SpacesPage] load failed:', err))
  }

  useEffect(() => { loadSpaces() }, [])

  return (
    <div className="h-full overflow-y-auto p-6 max-w-4xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <h1 className="font-heading font-900 text-h1 text-text-primary">
          Knowledge Spaces
        </h1>
        <button
          onClick={() => setCreateOpen(true)}
          className="bg-accent-blue text-white px-4 py-2 rounded-md
                     text-caption font-heading font-800 hover:opacity-90"
        >
          + Create Space
        </button>
      </div>

      <div className="space-y-3">
        {spaces.map((space) => (
          <div key={space.spaceId}
               className="bg-bg-secondary border border-citation-border rounded-lg p-4">
            <div className="flex justify-between items-start cursor-pointer"
                 onClick={() => setExpandedId(
                   expandedId === space.spaceId ? null : space.spaceId
                 )}>
              <div>
                <h3 className="font-heading font-800 text-body text-text-primary">
                  {space.name}
                </h3>
                <p className="text-caption text-text-secondary mt-0.5">
                  {space.description || 'No description'}
                </p>
                <div className="flex gap-4 mt-2 text-[11px] text-text-muted">
                  <span>Team: {space.ownerTeam}</span>
                  <span>Lang: {space.language}</span>
                  <span>Index: <code className="font-mono">{space.indexName}</code></span>
                  <span>Rules: {space.accessRules.length}</span>
                </div>
              </div>
              <span className="text-text-muted text-caption">
                {expandedId === space.spaceId ? '▲' : '▼'}
              </span>
            </div>
            {expandedId === space.spaceId && (
              <div className="mt-4 pt-4 border-t border-citation-border">
                <AccessRuleEditor
                  spaceId={space.spaceId}
                  rules={space.accessRules}
                  onUpdated={loadSpaces}
                />
              </div>
            )}
          </div>
        ))}
      </div>

      <CreateSpaceDialog open={createOpen}
                         onClose={() => setCreateOpen(false)} onCreated={loadSpaces} />
    </div>
  )
}
