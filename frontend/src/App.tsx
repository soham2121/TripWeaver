import { useEffect, useState } from 'react'
import { BrowserRouter, NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { api } from './api/client'
import { DiscoverPage } from './features/discover/DiscoverPage'

type Health = { status: string }

function ApiStatus() {
  const [status, setStatus] = useState<'loading' | 'up' | 'down'>('loading')
  useEffect(() => {
    api<Health>('/health')
      .then((h) => setStatus(h.status === 'UP' ? 'up' : 'down'))
      .catch(() => setStatus('down'))
  }, [])
  return (
    <span role="status" className="text-xs text-zinc-500">
      API: {status === 'loading' ? 'checking…' : status === 'up' ? 'connected' : 'unavailable'}
    </span>
  )
}

const nav = ({ isActive }: { isActive: boolean }) =>
  `rounded px-3 py-1.5 text-sm ${isActive ? 'bg-teal-700 text-white' : 'text-zinc-700 hover:bg-zinc-100'}`

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-zinc-50 text-zinc-900">
        <header className="border-b border-zinc-200 bg-white">
          <div className="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3">
            <span className="text-lg font-semibold text-teal-800">TripWeaver</span>
            <nav className="flex gap-1">
              <NavLink to="/discover" className={nav}>
                Discover
              </NavLink>
            </nav>
            <span className="ml-auto">
              <ApiStatus />
            </span>
          </div>
        </header>
        <main className="mx-auto max-w-6xl px-4 py-4">
          <Routes>
            <Route path="/" element={<Navigate to="/discover" replace />} />
            <Route path="/discover" element={<DiscoverPage />} />
          </Routes>
        </main>
        <footer className="mx-auto max-w-6xl px-4 py-6 text-xs text-zinc-500">
          Place data © OpenStreetMap contributors (ODbL). Map tiles: OpenFreeMap. Estimates are
          labelled; unknown costs are never shown as ₹0.
        </footer>
      </div>
    </BrowserRouter>
  )
}
