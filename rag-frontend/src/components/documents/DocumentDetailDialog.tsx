import { useEffect, useState } from 'react'
import { documentsApi } from '@/api/documents'
import { StatusBadge } from './StatusBadge'
import type { DocumentDetailResponse } from '@/api/types'

interface DocumentDetailDialogProps {
  spaceId: string
  documentId: string | null
  onClose: () => void
}

export function DocumentDetailDialog({ spaceId, documentId, onClose }: DocumentDetailDialogProps) {
  const [doc, setDoc] = useState<DocumentDetailResponse | null>(null)

  useEffect(() => {
    if (!documentId) return
    documentsApi.get(spaceId, documentId).then(setDoc)
  }, [documentId])

  if (!documentId) return null

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
         onClick={onClose}>
      <div className="bg-bg-secondary rounded-lg p-6 w-[560px] max-h-[80vh] overflow-y-auto"
           onClick={(e) => e.stopPropagation()}>
        {doc ? (
          <>
            <div className="flex justify-between items-start mb-4">
              <h2 className="font-heading font-800 text-h2 text-text-primary">
                {doc.title}
              </h2>
              <StatusBadge status={doc.status} />
            </div>
            <div className="grid grid-cols-2 gap-3 text-caption mb-4">
              <div>
                <span className="text-text-muted">Type:</span>{' '}
                <span className="text-text-secondary">{doc.fileType}</span>
              </div>
              <div>
                <span className="text-text-muted">Level:</span>{' '}
                <span className="text-text-secondary">{doc.securityLevel}</span>
              </div>
              <div>
                <span className="text-text-muted">Chunks:</span>{' '}
                <span className="text-text-secondary">{doc.chunkCount}</span>
              </div>
              <div>
                <span className="text-text-muted">Tags:</span>{' '}
                <span className="text-text-secondary">{doc.tags.join(', ') || '—'}</span>
              </div>
            </div>
            <h3 className="font-heading font-800 text-body text-text-primary mb-2">
              Version History
            </h3>
            <div className="space-y-2">
              {doc.versions.map((v) => (
                <div key={v.versionId}
                     className="bg-bg-tertiary rounded-md p-3 text-caption">
                  <div className="flex justify-between">
                    <span className="text-text-primary font-mono">v{v.versionNo}</span>
                    <span className="text-text-muted">
                      {(v.fileSize / 1024 / 1024).toFixed(1)} MB
                    </span>
                  </div>
                  <span className="text-[11px] text-text-muted">
                    {new Date(v.createdAt).toLocaleString()}
                  </span>
                </div>
              ))}
            </div>
          </>
        ) : (
          <p className="text-text-muted">Loading...</p>
        )}
        <div className="flex justify-end mt-4">
          <button onClick={onClose}
                  className="px-4 py-2 text-caption text-text-secondary
                             hover:text-text-primary">
            Close
          </button>
        </div>
      </div>
    </div>
  )
}
