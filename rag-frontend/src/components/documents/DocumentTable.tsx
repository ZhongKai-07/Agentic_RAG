import { useDocumentStore } from '@/stores/useDocumentStore'
import { StatusBadge } from './StatusBadge'
import type { DocumentResponse } from '@/api/types'

interface DocumentTableProps {
  onView: (doc: DocumentResponse) => void
  onRetry: (doc: DocumentResponse) => void
  onDelete: (doc: DocumentResponse) => void
}

export function DocumentTable({ onView, onRetry, onDelete }: DocumentTableProps) {
  const { documents, selectedIds, toggleSelect, selectAll, clearSelection } =
    useDocumentStore()

  const allSelected = documents.length > 0 && selectedIds.size === documents.length

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-caption">
        <thead>
          <tr className="border-b-2 border-text-primary text-text-muted text-left">
            <th className="p-3 w-10">
              <input
                type="checkbox"
                checked={allSelected}
                onChange={() =>
                  allSelected
                    ? clearSelection()
                    : selectAll(documents.map((d) => d.documentId))
                }
                className="accent-accent-blue"
              />
            </th>
            <th className="p-3">Name</th>
            <th className="p-3 w-20">Type</th>
            <th className="p-3 w-16">Ver</th>
            <th className="p-3 w-20">Level</th>
            <th className="p-3 w-24">Status</th>
            <th className="p-3 w-32">Tags</th>
            <th className="p-3 w-24">Actions</th>
          </tr>
        </thead>
        <tbody>
          {documents.map((doc) => (
            <tr
              key={doc.documentId}
              className="border-b border-dashed hover:bg-bg-tertiary
                         transition-colors"
            >
              <td className="p-3">
                <input
                  type="checkbox"
                  checked={selectedIds.has(doc.documentId)}
                  onChange={() => toggleSelect(doc.documentId)}
                  className="accent-accent-blue"
                />
              </td>
              <td className="p-3 text-text-primary">{doc.title}</td>
              <td className="p-3 text-text-secondary">{doc.fileType}</td>
              <td className="p-3 text-text-secondary">{doc.currentVersionNo}</td>
              <td className="p-3">
                <span className={doc.securityLevel === 'MANAGEMENT'
                  ? 'text-accent-purple' : 'text-text-secondary'}>
                  {doc.securityLevel}
                </span>
              </td>
              <td className="p-3">
                <StatusBadge status={doc.status} />
              </td>
              <td className="p-3">
                <div className="flex gap-1 flex-wrap">
                  {doc.tags.slice(0, 2).map((tag) => (
                    <span key={tag} className="bg-citation-bg text-text-secondary
                                               px-1.5 py-0.5 wobbly-border border border-text-primary text-[11px]">
                      {tag}
                    </span>
                  ))}
                  {doc.tags.length > 2 && (
                    <span className="text-text-muted text-[11px]">
                      +{doc.tags.length - 2}
                    </span>
                  )}
                </div>
              </td>
              <td className="p-3">
                <div className="flex gap-2">
                  <button
                    onClick={() => onView(doc)}
                    className="text-accent-blue hover:underline text-[11px]"
                  >
                    View
                  </button>
                  {doc.status === 'FAILED' && (
                    <button
                      onClick={() => onRetry(doc)}
                      className="text-status-parsing hover:underline text-[11px]"
                    >
                      Retry
                    </button>
                  )}
                  <button
                    onClick={() => onDelete(doc)}
                    className="text-text-muted hover:text-status-failed text-[11px]"
                  >
                    Del
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
