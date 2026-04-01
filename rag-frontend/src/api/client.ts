import { useAuthStore } from '@/stores/useAuthStore'
import type { ApiError } from './types'

const BASE = '/api/v1'

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const userId = useAuthStore.getState().userId
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  }
  if (userId) {
    headers['X-User-Id'] = userId
  }
  // Only set Content-Type for non-FormData bodies
  if (options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }

  const url = `${BASE}${path}`
  console.log(`[API] ${options.method || 'GET'} ${url}`)

  const res = await fetch(url, { ...options, headers })

  if (!res.ok) {
    const err: ApiError = await res.json().catch(() => ({
      error: 'UNKNOWN',
      message: res.statusText,
      timestamp: new Date().toISOString(),
    }))
    console.error(`[API] ${res.status} ${url}`, err)
    throw err
  }

  if (res.status === 204) return undefined as T
  const data = await res.json()
  console.log(`[API] ${res.status} ${url}`, data)
  return data
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'DELETE',
      body: body ? JSON.stringify(body) : undefined,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
    }),
}
