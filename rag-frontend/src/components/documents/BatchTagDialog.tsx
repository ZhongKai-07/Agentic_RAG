import { useState } from 'react'
import { documentsApi } from '@/api/documents'

interface BatchTagDialogProps {
  spaceId: string
  documentIds: string[]
  open: boolean
  onClose: () => void
  onDone: () => void
}

export function BatchTagDialog({ spaceId, documentIds, open, onClose, onDone }: BatchTagDialogProps) {
  const [tagsToAdd, setTagsToAdd] = useState('')
  const [tagsToRemove, setTagsToRemove] = useState('')

  if (!open) return null

  const handleSubmit = async () => {
    const add = tagsToAdd.split(',').map((t) => t.trim()).filter(Boolean)
    const remove = tagsToRemove.split(',').map((t) => t.trim()).filter(Boolean)
    await documentsApi.batchUpdateTags(spaceId, documentIds, add, remove)
    onDone()
    onClose()
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
         onClick={onClose}>
      <div className="modal-hand w-96 space-y-4"
           onClick={(e) => e.stopPropagation()}>
        <h2 className="font-heading font-800 text-h2 text-text-primary">
          Batch Update Tags
        </h2>
        <p className="text-caption text-text-secondary">
          Updating {documentIds.length} document(s)
        </p>
        <div>
          <label className="text-caption text-text-muted">Tags to add (comma-separated)</label>
          <input value={tagsToAdd} onChange={(e) => setTagsToAdd(e.target.value)}
                 className="input-hand w-full mt-1 text-caption" />
        </div>
        <div>
          <label className="text-caption text-text-muted">Tags to remove (comma-separated)</label>
          <input value={tagsToRemove} onChange={(e) => setTagsToRemove(e.target.value)}
                 className="input-hand w-full mt-1 text-caption" />
        </div>
        <div className="flex justify-end gap-3">
          <button onClick={onClose}
                  className="btn-hand bg-white px-4 py-2 text-caption text-text-secondary">Cancel</button>
          <button onClick={handleSubmit}
                  className="bg-accent-blue text-white px-4 py-2 btn-hand
                             text-caption font-heading font-800">Apply</button>
        </div>
      </div>
    </div>
  )
}
