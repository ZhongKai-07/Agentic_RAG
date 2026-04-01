import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserResponse } from '@/api/types'

interface AuthState {
  userId: string | null
  user: UserResponse | null
  setUserId: (id: string) => void
  setUser: (user: UserResponse) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      userId: null,
      user: null,
      setUserId: (id) => set({ userId: id }),
      setUser: (user) => set({ user }),
      logout: () => set({ userId: null, user: null }),
    }),
    { name: 'rag-auth' }
  )
)
