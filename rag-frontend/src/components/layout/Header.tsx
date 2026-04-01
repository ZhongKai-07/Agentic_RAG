import { Link, useLocation } from 'react-router-dom'
import { SpaceSelector } from './SpaceSelector'
import { useAuthStore } from '@/stores/useAuthStore'
import { cn } from '@/lib/utils'

const navItems = [
  { path: '/chat', label: 'Chat' },
  { path: '/documents', label: 'Documents' },
  { path: '/spaces', label: 'Spaces' },
]

export function Header() {
  const location = useLocation()
  const { user, logout } = useAuthStore()

  return (
    <header className="h-14 bg-white border-b-2 border-dashed border-text-primary
                        flex items-center justify-between px-6">
      <div className="flex items-center gap-6">
        <h1 className="font-heading font-900 text-body text-accent-purple rotate-[-2deg] inline-block">
          RAG KB
        </h1>
        <nav className="flex gap-1">
          {navItems.map((item) => (
            <Link
              key={item.path}
              to={item.path}
              className={cn(
                'px-3 py-1.5 wobbly-border text-caption hover:rotate-1 transition-transform duration-150',
                location.pathname === item.path
                  ? 'bg-bg-tertiary text-text-primary shadow-hard-sm border-2 border-text-primary'
                  : 'text-text-secondary hover:text-text-primary'
              )}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </div>
      <div className="flex items-center gap-4">
        <SpaceSelector />
        <div className="flex items-center gap-2">
          <span className="text-caption text-text-secondary">
            {user?.displayName || user?.username}
          </span>
          <button
            onClick={logout}
            className="text-caption text-text-muted hover:text-text-secondary hover:line-through"
          >
            Logout
          </button>
        </div>
      </div>
    </header>
  )
}
