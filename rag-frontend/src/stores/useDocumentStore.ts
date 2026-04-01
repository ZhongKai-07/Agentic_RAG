import { create } from 'zustand'
import type { DocumentResponse } from '@/api/types'

interface DocumentState {
  documents: DocumentResponse[]
  totalElements: number
  totalPages: number
  currentPage: number
  searchQuery: string
  selectedIds: Set<string>
  // Actions
  setDocuments: (docs: DocumentResponse[], total: number, totalPages: number) => void
  setCurrentPage: (page: number) => void
  setSearchQuery: (query: string) => void
  toggleSelect: (id: string) => void
  selectAll: (ids: string[]) => void
  clearSelection: () => void
  updateDocumentStatus: (docId: string, status: string, chunkCount?: number) => void
}

export const useDocumentStore = create<DocumentState>((set) => ({
  documents: [],
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
  searchQuery: '',
  selectedIds: new Set(),

  setDocuments: (docs, total, totalPages) =>
    set({ documents: docs, totalElements: total, totalPages }),
  setCurrentPage: (page) => set({ currentPage: page }),
  setSearchQuery: (query) => set({ searchQuery: query, currentPage: 0 }),
  toggleSelect: (id) =>
    set((s) => {
      const next = new Set(s.selectedIds)
      next.has(id) ? next.delete(id) : next.add(id)
      return { selectedIds: next }
    }),
  selectAll: (ids) => set({ selectedIds: new Set(ids) }),
  clearSelection: () => set({ selectedIds: new Set() }),
  updateDocumentStatus: (docId, status, chunkCount) =>
    set((s) => ({
      documents: s.documents.map((d) =>
        d.documentId === docId
          ? { ...d, status: status as DocumentResponse['status'], ...(chunkCount !== undefined ? { chunkCount } : {}) }
          : d
      ),
    })),
}))
