import { useEffect, useState } from 'react'
import { BrowserRouter, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { api } from './api/client'
import { DiscoverPage } from './features/discover/DiscoverPage'
import { HomePage } from './features/home/HomePage'
import { PlanPage } from './features/plan/PlanPage'
import { RoadTripPage } from './features/roadtrip/RoadTripPage'

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

/** Remounts a page when its query (mode or demo) changes, so the form starts fresh. */
function Fresh({ page: Page }: { page: () => React.JSX.Element }) {
  return <Page key={useLocation().search} />
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
            <nav className="flex flex-wrap gap-1">
              <NavLink to="/" end className={nav}>
                Home
              </NavLink>
              <NavLink to="/discover" className={nav}>
                Discover
              </NavLink>
              <NavLink to="/plan" className={nav}>
                Plan
              </NavLink>
              <NavLink to="/road-trip" className={nav}>
                Road trip
              </NavLink>
            </nav>
            <span className="ml-auto">
              <ApiStatus />
            </span>
          </div>
        </header>
        <main className="mx-auto max-w-6xl px-4 py-4">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/discover" element={<DiscoverPage />} />
            <Route path="/plan" element={<Fresh page={PlanPage} />} />
            <Route path="/road-trip" element={<Fresh page={RoadTripPage} />} />
            <Route path="*" element={<Navigate to="/" replace />} />
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
