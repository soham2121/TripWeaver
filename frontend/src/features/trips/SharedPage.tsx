import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { tripsApi } from '../../api/trips'
import type { Itinerary } from '../../types/itinerary'
import type { RoadTrip } from '../../types/roadtrip'
import type { SharedTrip } from '../../types/trip'
import { localTime } from '../../utils/format'
import { ItineraryView } from '../plan/ItineraryView'
import { RoadTripView } from '../roadtrip/RoadTripPage'

/** Public read-only view of a trip opened from a share link. No sign-in needed. */
export function SharedPage() {
  const { token = '' } = useParams()
  const [trip, setTrip] = useState<SharedTrip | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    tripsApi
      .shared(token)
      .then(setTrip)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load the shared trip.'))
  }, [token])

  if (error)
    return (
      <p role="alert" className="rounded border border-amber-300 bg-amber-50 p-3 text-sm">
        {error}
      </p>
    )
  if (!trip) return <p role="status">Loading shared trip…</p>

  const it = trip.kind === 'ITINERARY' ? (trip.result as Itinerary | null) : null
  const start = it?.days[0]?.route?.geometry[0]
  const firstStop = it?.days[0]?.stops[0]?.place
  const base = start
    ? { lat: start[1], lon: start[0] }
    : { lat: firstStop?.lat ?? 15.5, lon: firstStop?.lon ?? 73.8 }

  return (
    <div className="space-y-3">
      <div className="card p-3 text-sm">
        <h1 className="text-2xl font-bold">{trip.title}</h1>
        <p className="text-xs text-zinc-600">
          Read-only shared trip · updated {localTime(trip.updatedAt)}
        </p>
      </div>
      {!trip.result && <p className="text-sm text-zinc-500">This trip has no plan yet.</p>}
      {it && <ItineraryView it={it} base={base} />}
      {trip.kind === 'ROAD_TRIP' && trip.result && <RoadTripView trip={trip.result as RoadTrip} />}
    </div>
  )
}
