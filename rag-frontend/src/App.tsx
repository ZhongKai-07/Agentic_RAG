import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'
import { AppLayout } from '@/components/layout/AppLayout'
import { ChatPage } from '@/components/chat/ChatPage'
import { DocumentsPage } from '@/components/documents/DocumentsPage'
import { SpacesPage } from '@/components/spaces/SpacesPage'
import { LoginPage } from '@/pages/LoginPage'

export default function App() {
  const userId = useAuthStore((s) => s.userId)

  if (!userId) {
    return <LoginPage />
  }

  return (
    <AppLayout>
      <Routes>
        <Route path="/" element={<Navigate to="/chat" replace />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/documents" element={<DocumentsPage />} />
        <Route path="/spaces" element={<SpacesPage />} />
      </Routes>
    </AppLayout>
  )
}
