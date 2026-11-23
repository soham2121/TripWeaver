import { useEffect, useState } from 'react'
import {
  BrowserRouter,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom'
import { api } from './api/client'
import { authApi } from './api/trips'
import { useAuth } from './features/auth/auth'
import { AuthProvider } from './features/auth/AuthProvider'
import { LoginPage } from './features/auth/LoginPage'
import { DiscoverPage } from './features/discover/DiscoverPage'
import { HomePage } from './features/home/HomePage'
import { PlanPage } from './features/plan/PlanPage'
import { RoadTripPage } from './features/roadtrip/RoadTripPage'
import { SharedPage } from './features/trips/SharedPage'
import { TripPage } from './features/trips/TripPage'
import { TripsPage } from './features/trips/TripsPage'

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

/** Remounts a page when its query (mode, demo) or path (trip id) changes, so state starts fresh. */
function Fresh({
  page: Page,
  by = 'search',
}: {
  page: () => React.JSX.Element
  by?: 'search' | 'path'
}) {
  const l = useLocation()
  return <Page key={by === 'path' ? l.pathname : l.search} />
}

function Account() {
  const { user, loading, setUser } = useAuth()
  const navigate = useNavigate()
  if (loading) return null
  if (!user)
    return (
      <NavLink to="/login" className={nav}>
        Sign in
      </NavLink>
    )
  return (
    <span className="flex items-center gap-2 text-sm">
      <NavLink to="/trips" className={nav}>
        My trips
      </NavLink>
      <span className="text-zinc-600">{user.displayName ?? user.username}</span>
      <button
        type="button"
        className="text-xs text-teal-700 underline"
        onClick={() =>
          void authApi.logout().finally(() => {
            setUser(null)
            navigate('/')
          })
        }
      >
        Sign out
      </button>
    </span>
  )
}

const nav = ({ isActive }: { isActive: boolean }) =>
  `rounded px-3 py-1.5 text-sm ${isActive ? 'bg-teal-700 text-white' : 'text-zinc-700 hover:bg-zinc-100'}`

export default function App() {
  return (
    <AuthProvider>
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
              <span className="ml-auto flex items-center gap-3">
                <Account />
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
              <Route path="/login" element={<LoginPage />} />
              <Route path="/trips" element={<TripsPage />} />
              <Route path="/trips/:id" element={<Fresh page={TripPage} by="path" />} />
              <Route path="/shared/:token" element={<SharedPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </main>
          <footer className="mx-auto max-w-6xl px-4 py-6 text-xs text-zinc-500">
            Place data © OpenStreetMap contributors (ODbL). Map tiles: OpenFreeMap. Estimates are
            labelled; unknown costs are never shown as ₹0.
          </footer>
        </div>
      </BrowserRouter>
    </AuthProvider>
  )
}
