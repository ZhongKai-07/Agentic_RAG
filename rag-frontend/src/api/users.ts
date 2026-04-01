import { api } from './client'
import type { UserResponse } from './types'

export const usersApi = {
  getMe: () => api.get<UserResponse>('/users/me'),
}
