import { useEffect, useState, useCallback } from 'react'
import { useSpaceStore } from '@/stores/useSpaceStore'
import { useDocumentStore } from '@/stores/useDocumentStore'
import { documentsApi } from '@/api/documents'
import { DocumentTable } from './DocumentTable'
import { UploadDialog } from './UploadDialog'
import { DocumentDetailDialog } from './DocumentDetailDialog'
import { BatchTagDialog } from './BatchTagDialog'
import type { DocumentResponse } from '@/api/types'

export function DocumentsPage() {
  const spaceId = useSpaceStore((s) => s.currentSpaceId)
  const {
    documents, totalElements, totalPages, currentPage, searchQuery, selectedIds,
    setDocuments, setCurrentPage, setSearchQuery, clearSelection,
  } = useDocumentStore()

  const [uploadOpen, setUploadOpen] = useState(false)
  const [detailDocId, setDetailDocId] = useState<string | null>(null)
  const [tagDialogOpen, setTagDialogOpen] = useState(false)

  const loadDocuments = useCallback(() => {
    if (!spaceId) return
    documentsApi.list(spaceId, currentPage, 20, searchQuery || undefined)
      .then((page) => {
        console.log('[DocumentsPage] loaded:', page)
        setDocuments(page.items, page.totalElements, page.totalPages)
      })
      .catch((err) => console.error('[DocumentsPage] load failed:', err))
  }, [spaceId, currentPage, searchQuery])

  useEffect(() => { loadDocuments() }, [loadDocuments])

  const handleRetry = async (doc: DocumentResponse) => {
    if (!spaceId) return
    await documentsApi.retry(spaceId, doc.documentId)
    loadDocuments()
  }

  const handleDelete = async (doc: DocumentResponse) => {
    if (!spaceId) return
    await documentsApi.delete(spaceId, doc.documentId)
    loadDocuments()
  }

  const handleBatchDelete = async () => {
    if (!spaceId || selectedIds.size === 0) return
    await documentsApi.batchDelete(spaceId, Array.from(selectedIds))
    clearSelection()
    loadDocuments()
  }

  if (!spaceId) {
    return (
      <div className="h-full flex items-center justify-center text-text-muted">
        Select a knowledge space
      </div>
    )
  }

  // Count stats
  const indexed = documents.filter((d) => d.status === 'INDEXED').length
  const parsing = documents.filter((d) => ['PARSING', 'INDEXING'].includes(d.status)).length
  const failed = documents.filter((d) => d.status === 'FAILED').length

  return (
    <div className="h-full flex flex-col">
      {/* Toolbar */}
      <div className="flex items-center justify-between p-4 border-b-2 border-dashed border-text-primary">
        <div className="flex gap-2">
          <button
            onClick={() => setUploadOpen(true)}
            className="bg-accent-blue text-white px-4 py-2 btn-hand
                       text-caption font-heading font-800"
          >
            Upload
          </button>
          {selectedIds.size > 0 && (
            <>
              <button
                onClick={() => setTagDialogOpen(true)}
                className="bg-bg-tertiary text-text-primary px-3 py-2 btn-hand
                           text-caption"
              >
                Batch Tag ({selectedIds.size})
              </button>
              <button
                onClick={handleBatchDelete}
                className="bg-bg-tertiary text-status-failed px-3 py-2 btn-hand
                           text-caption"
              >
                Delete ({selectedIds.size})
              </button>
            </>
          )}
        </div>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search documents..."
          className="input-hand w-64 text-caption"
        />
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto">
        <DocumentTable
          onView={(doc) => setDetailDocId(doc.documentId)}
          onRetry={handleRetry}
          onDelete={handleDelete}
        />
      </div>

      {/* Footer stats + pagination */}
      <div className="flex items-center justify-between px-4 py-3
                      border-t-2 border-dashed border-text-primary text-caption text-text-muted">
        <span>
          {totalElements} documents | Indexed: {indexed} | Processing: {parsing} | Failed: {failed}
        </span>
        <div className="flex gap-2">
          <button
            disabled={currentPage === 0}
            onClick={() => setCurrentPage(currentPage - 1)}
            className="px-3 py-1 btn-hand text-caption
                       disabled:opacity-30"
          >
            Prev
          </button>
          <span className="px-2 py-1">
            {currentPage + 1} / {Math.max(totalPages, 1)}
          </span>
          <button
            disabled={currentPage >= totalPages - 1}
            onClick={() => setCurrentPage(currentPage + 1)}
            className="px-3 py-1 btn-hand text-caption
                       disabled:opacity-30"
          >
            Next
          </button>
        </div>
      </div>

      {/* Dialogs */}
      <UploadDialog spaceId={spaceId} open={uploadOpen}
                    onClose={() => setUploadOpen(false)} onUploaded={loadDocuments} />
      <DocumentDetailDialog spaceId={spaceId} documentId={detailDocId}
                            onClose={() => setDetailDocId(null)} />
      <BatchTagDialog spaceId={spaceId} documentIds={Array.from(selectedIds)}
                      open={tagDialogOpen} onClose={() => setTagDialogOpen(false)}
                      onDone={loadDocuments} />
    </div>
  )
}
