import { api } from './client'
import type {
  DocumentResponse,
  DocumentDetailResponse,
  VersionResponse,
  PageResult,
} from './types'

export const documentsApi = {
  upload: (spaceId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<DocumentResponse>(`/spaces/${spaceId}/documents/upload`, form)
  },

  batchUpload: (spaceId: string, files: File[]) => {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return api.post<DocumentResponse[]>(`/spaces/${spaceId}/documents/batch-upload`, form)
  },

  list: (spaceId: string, page = 0, size = 20, search?: string) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (search) params.set('search', search)
    return api.get<PageResult<DocumentResponse>>(
      `/spaces/${spaceId}/documents?${params}`
    )
  },

  get: (spaceId: string, docId: string) =>
    api.get<DocumentDetailResponse>(`/spaces/${spaceId}/documents/${docId}`),

  delete: (spaceId: string, docId: string) =>
    api.delete<void>(`/spaces/${spaceId}/documents/${docId}`),

  uploadVersion: (spaceId: string, docId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<DocumentResponse>(
      `/spaces/${spaceId}/documents/${docId}/versions`,
      form
    )
  },

  getVersions: (spaceId: string, docId: string) =>
    api.get<VersionResponse[]>(`/spaces/${spaceId}/documents/${docId}/versions`),

  retry: (spaceId: string, docId: string) =>
    api.post<DocumentResponse>(`/spaces/${spaceId}/documents/${docId}/retry`),

  batchUpdateTags: (
    spaceId: string,
    documentIds: string[],
    tagsToAdd: string[],
    tagsToRemove: string[]
  ) =>
    api.put<void>(`/spaces/${spaceId}/documents/batch-tags`, {
      documentIds,
      tagsToAdd,
      tagsToRemove,
    }),

  batchDelete: (spaceId: string, documentIds: string[]) =>
    api.delete<void>(`/spaces/${spaceId}/documents/batch-delete`, { documentIds }),
}
