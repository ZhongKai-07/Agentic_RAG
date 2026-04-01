import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { SpaceResponse } from '@/api/types'

interface SpaceState {
  spaces: SpaceResponse[]
  currentSpaceId: string | null
  setSpaces: (spaces: SpaceResponse[]) => void
  setCurrentSpaceId: (id: string) => void
  getCurrentSpace: () => SpaceResponse | undefined
}

export const useSpaceStore = create<SpaceState>()(
  persist(
    (set, get) => ({
      spaces: [],
      currentSpaceId: null,
      setSpaces: (spaces) => set({ spaces }),
      setCurrentSpaceId: (id) => set({ currentSpaceId: id }),
      getCurrentSpace: () => {
        const { spaces, currentSpaceId } = get()
        return spaces.find((s) => s.spaceId === currentSpaceId)
      },
    }),
    { name: 'rag-space' }
  )
)
