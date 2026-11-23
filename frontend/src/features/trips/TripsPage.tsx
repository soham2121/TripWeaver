import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { tripsApi } from '../../api/trips'
import type { TripSummary } from '../../types/trip'
import { localTime } from '../../utils/format'
import { useAuth } from '../auth/auth'

/** Trips the user owns or was added to. */
export function TripsPage() {
  const { user, loading } = useAuth()
  const [trips, setTrips] = useState<TripSummary[] | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    if (!user) return
    tripsApi
      .list()
      .then(setTrips)
      .catch(() => setError(true))
  }, [user])

  if (loading) return <p role="status">Loading…</p>
  if (!user)
    return (
      <p className="text-sm">
        <Link to="/login?next=/trips" className="text-teal-700 underline">
          Sign in
        </Link>{' '}
        to see your saved and shared trips.
      </p>
    )
  return (
    <div className="space-y-2 text-sm">
      <h1 className="text-base font-semibold">My trips</h1>
      {error && <p role="alert">Could not load your trips.</p>}
      {trips?.length === 0 && (
        <p className="text-zinc-500">
          No trips yet.{' '}
          <Link to="/plan" className="text-teal-700 underline">
            Plan one
          </Link>{' '}
          and save it, or ask a friend to add you to theirs.
        </p>
      )}
      <ul className="space-y-1">
        {trips?.map((t) => (
          <li key={t.id}>
            <Link
              to={`/trips/${t.id}`}
              className="block rounded border border-zinc-200 bg-white p-2 hover:border-teal-600"
            >
              <span className="font-medium">{t.title}</span>{' '}
              <span className="text-xs text-zinc-500">
                {t.kind === 'ROAD_TRIP' ? 'road trip' : 'itinerary'} · {t.role.toLowerCase()} ·
                owner {t.ownerUsername} · updated {localTime(t.updatedAt)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
