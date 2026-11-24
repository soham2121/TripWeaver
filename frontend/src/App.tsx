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
    <span role="status" className="flex items-center gap-1.5 text-xs text-zinc-500">
      <span
        aria-hidden
        className={`size-1.5 rounded-full ${status === 'up' ? 'bg-emerald-500' : status === 'down' ? 'bg-red-500' : 'animate-pulse bg-zinc-400'}`}
      />
      <span className="sr-only sm:not-sr-only">
        API: {status === 'loading' ? 'checking…' : status === 'up' ? 'connected' : 'unavailable'}
      </span>
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
      <NavLink to="/login" viewTransition className={nav}>
        Sign in
      </NavLink>
    )
  return (
    <span className="flex items-center gap-2 text-sm">
      <NavLink to="/trips" viewTransition className={nav}>
        My trips
      </NavLink>
      <span className="text-zinc-600">{user.displayName ?? user.username}</span>
      <button
        type="button"
        className="rounded px-2 py-1 text-xs font-medium text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900"
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

/** Nav item. The active pill carries a view-transition name so it slides between tabs on navigation. */
const nav = ({ isActive }: { isActive: boolean }) =>
  `relative shrink-0 rounded-full px-3.5 py-1.5 text-sm font-medium whitespace-nowrap ${isActive ? 'bg-teal-700 text-white shadow-sm [view-transition-name:nav-pill]' : 'text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900'}`

/** Brand mark: two routes woven together, indigo over clay. */
function Logo() {
  return (
    <span className="flex items-center gap-2">
      <svg viewBox="0 0 24 24" className="size-7" aria-hidden>
        <rect width="24" height="24" rx="7" className="fill-teal-700" />
        <path
          d="M5 16c3-0 4-8 7-8s4 8 7 8"
          fill="none"
          strokeWidth="2.2"
          strokeLinecap="round"
          className="stroke-clay-500"
        />
        <path
          d="M5 8c3 0 4 8 7 8s4-8 7-8"
          fill="none"
          strokeWidth="2.2"
          strokeLinecap="round"
          className="stroke-white"
        />
      </svg>
      <span className="font-display text-lg font-bold tracking-tight text-zinc-900">
        TripWeaver
      </span>
    </span>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <div className="flex min-h-screen flex-col">
          <header className="sticky top-0 z-30 border-b border-zinc-200 bg-white/95">
            <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
              <NavLink to="/" viewTransition aria-label="TripWeaver home" className="rounded-lg">
                <Logo />
              </NavLink>
              <nav
                className="order-3 -mx-1 flex w-full gap-1 overflow-x-auto px-1 sm:order-none sm:w-auto"
                aria-label="Main"
              >
                <NavLink to="/" end viewTransition className={nav}>
                  Home
                </NavLink>
                <NavLink to="/discover" viewTransition className={nav}>
                  Discover
                </NavLink>
                <NavLink to="/plan" viewTransition className={nav}>
                  Plan
                </NavLink>
                <NavLink to="/road-trip" viewTransition className={nav}>
                  Road trip
                </NavLink>
              </nav>
              <span className="order-2 ml-auto flex items-center gap-3 sm:order-none">
                <Account />
                <ApiStatus />
              </span>
            </div>
          </header>
          <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
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
          <footer className="mx-auto w-full max-w-6xl border-t border-zinc-200 px-4 py-6 text-xs text-zinc-500">
            Place data © OpenStreetMap contributors (ODbL). Map tiles: OpenFreeMap. Estimates are
            labelled; unknown costs are never shown as ₹0.
          </footer>
        </div>
      </BrowserRouter>
    </AuthProvider>
  )
}
