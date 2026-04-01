import { useState } from 'react'
import { spacesApi } from '@/api/spaces'

interface CreateSpaceDialogProps {
  open: boolean
  onClose: () => void
  onCreated: () => void
}

export function CreateSpaceDialog({ open, onClose, onCreated }: CreateSpaceDialogProps) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [ownerTeam, setOwnerTeam] = useState('')
  const [language, setLanguage] = useState('zh')
  const [indexName, setIndexName] = useState('')
  const [error, setError] = useState('')

  if (!open) return null

  const handleCreate = async () => {
    setError('')
    try {
      const result = await spacesApi.create({ name, description, ownerTeam, language, indexName })
      console.log('[CreateSpace] created:', result)
      setName(''); setDescription(''); setOwnerTeam(''); setIndexName('')
      onCreated()
      onClose()
    } catch (err: unknown) {
      const msg = (err as { message?: string })?.message || 'Create failed'
      console.error('[CreateSpace] failed:', err)
      setError(msg)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
         onClick={onClose}>
      <div className="bg-bg-secondary rounded-lg p-6 w-[480px] space-y-4"
           onClick={(e) => e.stopPropagation()}>
        <h2 className="font-heading font-800 text-h2 text-text-primary">
          Create Knowledge Space
        </h2>
        {[
          { label: 'Name', value: name, set: setName, required: true },
          { label: 'Description', value: description, set: setDescription },
          { label: 'Owner Team', value: ownerTeam, set: setOwnerTeam, required: true },
          { label: 'Index Name', value: indexName, set: setIndexName, required: true,
            placeholder: 'e.g. kb_compliance_v1' },
        ].map((field) => (
          <div key={field.label}>
            <label className="text-caption text-text-muted">
              {field.label} {field.required && '*'}
            </label>
            <input
              value={field.value}
              onChange={(e) => field.set(e.target.value)}
              placeholder={field.placeholder}
              className="w-full bg-bg-tertiary text-text-primary rounded-md px-3 py-2
                         border border-citation-border mt-1 text-caption"
            />
          </div>
        ))}
        <div>
          <label className="text-caption text-text-muted">Language *</label>
          <select value={language} onChange={(e) => setLanguage(e.target.value)}
                  className="w-full bg-bg-tertiary text-text-primary rounded-md px-3 py-2
                             border border-citation-border mt-1 text-caption">
            <option value="zh">Chinese</option>
            <option value="en">English</option>
          </select>
        </div>
        {error && <p className="text-status-failed text-caption">{error}</p>}
        <div className="flex justify-end gap-3">
          <button onClick={onClose}
                  className="px-4 py-2 text-caption text-text-secondary">Cancel</button>
          <button
            onClick={handleCreate}
            disabled={!name || !ownerTeam || !indexName}
            className="bg-accent-blue text-white px-4 py-2 rounded-md
                       text-caption font-heading font-800 disabled:opacity-40"
          >
            Create
          </button>
        </div>
      </div>
    </div>
  )
}
