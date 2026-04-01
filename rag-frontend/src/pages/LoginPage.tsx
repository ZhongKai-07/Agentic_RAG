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
      <div className="modal-hand w-96 rotate-[-1deg] space-y-6">
        <div className="w-4 h-4 bg-accent-blue rounded-full shadow-hard-sm mx-auto -mt-2 mb-2" />
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
          className="input-hand w-full text-code"
        />
        {error && <p className="text-status-failed text-caption">{error}</p>}
        <button
          onClick={handleLogin}
          className="w-full bg-accent-blue text-white btn-hand py-2
                     font-heading font-800"
        >
          Enter
        </button>
      </div>
    </div>
  )
}
