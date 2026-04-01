import { useState, useRef } from 'react'
import { documentsApi } from '@/api/documents'

interface UploadDialogProps {
  spaceId: string
  open: boolean
  onClose: () => void
  onUploaded: () => void
}

export function UploadDialog({ spaceId, open, onClose, onUploaded }: UploadDialogProps) {
  const [files, setFiles] = useState<File[]>([])
  const [uploading, setUploading] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  if (!open) return null

  const handleUpload = async () => {
    if (files.length === 0) return
    setUploading(true)
    try {
      if (files.length === 1) {
        await documentsApi.upload(spaceId, files[0])
      } else {
        await documentsApi.batchUpload(spaceId, files)
      }
      setFiles([])
      onUploaded()
      onClose()
    } catch (err) {
      console.error('Upload failed:', err)
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
         onClick={onClose}>
      <div className="bg-bg-secondary rounded-lg p-6 w-[480px] space-y-4"
           onClick={(e) => e.stopPropagation()}>
        <h2 className="font-heading font-800 text-h2 text-text-primary">
          Upload Documents
        </h2>
        <div
          className="border-2 border-dashed border-citation-border rounded-lg p-8
                     text-center cursor-pointer hover:border-accent-blue transition-colors"
          onClick={() => inputRef.current?.click()}
        >
          <p className="text-text-secondary text-caption">
            Click to select files (PDF, WORD, EXCEL)
          </p>
          <p className="text-text-muted text-[11px] mt-1">Max 100MB per file</p>
          <input
            ref={inputRef}
            type="file"
            multiple
            accept=".pdf,.doc,.docx,.xls,.xlsx"
            className="hidden"
            onChange={(e) => setFiles(Array.from(e.target.files || []))}
          />
        </div>
        {files.length > 0 && (
          <div className="space-y-1">
            {files.map((f, i) => (
              <div key={i} className="flex justify-between text-caption text-text-secondary">
                <span>{f.name}</span>
                <span className="text-text-muted">
                  {(f.size / 1024 / 1024).toFixed(1)} MB
                </span>
              </div>
            ))}
          </div>
        )}
        <div className="flex justify-end gap-3">
          <button onClick={onClose}
                  className="px-4 py-2 text-caption text-text-secondary
                             hover:text-text-primary">
            Cancel
          </button>
          <button
            onClick={handleUpload}
            disabled={files.length === 0 || uploading}
            className="bg-accent-blue text-white px-4 py-2 rounded-md
                       text-caption font-heading font-800
                       disabled:opacity-40"
          >
            {uploading ? 'Uploading...' : `Upload ${files.length} file(s)`}
          </button>
        </div>
      </div>
    </div>
  )
}
