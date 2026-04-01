import { useState } from 'react'
import { useAuthStore } from '@/stores/useAuthStore'
import { usersApi } from '@/api/users'

export function LoginPage() {
  const [inputId, setInputId] = useState('11111111-1111-1111-1111-111111111111')
  const [error, setError] = useState('')
  const { setUserId, setUser } = useAuthStore()

  const handleLogin = async () => {
    setError('')
    try {
      useAuthStore.getState().setUserId(inputId)
      const user = await usersApi.getMe()
      setUser(user)
      setUserId(inputId)
    } catch {
      setError('User not found. Make sure the user exists in the database.')
      useAuthStore.getState().logout()
    }
  }

  return (
    <div className="min-h-screen bg-bg-primary flex items-center justify-center">
      <div className="bg-bg-secondary rounded-lg p-8 w-96 space-y-6">
        <h1 className="font-heading text-h2 font-800 text-text-primary">
          RAG Knowledge Base
        </h1>
        <p className="text-text-secondary text-caption">
          Enter your User ID to continue (mock auth)
        </p>
        <input
          type="text"
          value={inputId}
          onChange={(e) => setInputId(e.target.value)}
          placeholder="User UUID"
          className="w-full bg-bg-tertiary text-text-primary rounded-md px-4 py-2
                     border border-citation-border focus:border-accent-blue
                     focus:outline-none font-mono text-code"
        />
        {error && <p className="text-status-failed text-caption">{error}</p>}
        <button
          onClick={handleLogin}
          className="w-full bg-accent-blue text-white rounded-md py-2
                     font-heading font-800 hover:opacity-90 transition-opacity"
        >
          Enter
        </button>
      </div>
    </div>
  )
}
