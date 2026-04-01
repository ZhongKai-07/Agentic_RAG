import { Header } from './Header'

export function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="h-screen flex flex-col bg-bg-primary">
      <Header />
      <main className="flex-1 overflow-hidden">{children}</main>
    </div>
  )
}
