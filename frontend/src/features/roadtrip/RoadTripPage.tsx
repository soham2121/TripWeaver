import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { placesApi } from '../../api/places'
import { roadTripsApi } from '../../api/roadTrips'
import { BudgetTable } from '../../components/BudgetTable'
import { ConfidenceBadge } from '../../components/ConfidenceBadge'
import { toProblem, type Problem } from '../../api/client'
import { ProblemNotice, ProviderNotice } from '../../components/ProblemNotice'
import type { MapPoint } from '../../maps/PlaceMap'
import type { Confidence, Suggestion } from '../../types/place'
import { INTERESTS } from '../../types/place'
import type {
  RoadTrip,
  RoadTripDay,
  RoadTripItem,
  RoadTripRequest,
  Waypoint,
} from '../../types/roadtrip'
import { label } from '../../utils/format'
import { Autocomplete } from '../discover/Autocomplete'
import { SaveTrip } from '../trips/SaveTrip'

const PlaceMap = lazy(() => import('../../maps/PlaceMap'))

const CATALOGUES = ['konkan', 'goa']

interface Form {
  origin: Waypoint
  destination: Waypoint
  waypoints: Waypoint[]
  departureDate: string
  departureTime: string
  arriveBy: string
  maxDrivingHoursPerDay: number
  weights: Record<string, number>
  maxDetourMinutes: number
  maxDetours: number
  partySize: number
  transport: 'CAR' | 'SCOOTER' | 'TAXI'
  mileageKmPerLitre: string
  fuelPricePerLitre: string
  tollsTotal: string
  accommodationPerNight: string
  foodPerPersonPerDay: string
}

const tomorrow = () => {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const DEFAULT: Form = {
  origin: { name: 'Mumbai (CSMT)', lat: 18.9398, lon: 72.8355 },
  destination: { name: 'Candolim', lat: 15.5202, lon: 73.7659 },
  waypoints: [],
  departureDate: '',
  departureTime: '07:00',
  arriveBy: '',
  maxDrivingHoursPerDay: 7,
  weights: {},
  maxDetourMinutes: 45,
  maxDetours: 3,
  partySize: 2,
  transport: 'CAR',
  mileageKmPerLitre: '',
  fuelPricePerLitre: '',
  tollsTotal: '',
  accommodationPerNight: '',
  foodPerPersonPerDay: '',
}

const DEMOS: Record<string, Partial<Form>> = {
  'mumbai-goa': {
    weights: { heritage: 5, nature: 3, beach: 3 },
    maxDrivingHoursPerDay: 6,
    mileageKmPerLitre: '15',
    fuelPricePerLitre: '101',
  },
}

function toRequest(f: Form): RoadTripRequest {
  const num = (s: string) => (Number(s) > 0 ? Number(s) : undefined)
  return {
    origin: f.origin,
    destination: f.destination,
    waypoints: f.waypoints,
    departureDate: f.departureDate || tomorrow(),
    departureTime: f.departureTime,
    arriveBy: f.arriveBy || undefined,
    maxDrivingHoursPerDay: f.maxDrivingHoursPerDay,
    interests: f.weights,
    maxDetourMinutes: f.maxDetourMinutes,
    maxDetours: f.maxDetours,
    partySize: f.partySize,
    transport: f.transport,
    mileageKmPerLitre: num(f.mileageKmPerLitre),
    fuelPricePerLitre: num(f.fuelPricePerLitre),
    tollsTotal: num(f.tollsTotal),
    accommodationPerNight: num(f.accommodationPerNight),
    foodPerPersonPerDay: num(f.foodPerPersonPerDay),
  }
}

async function resolve(s: Suggestion): Promise<Waypoint | null> {
  if (s.type !== 'PLACE' || !s.placeId) return null
  const d = await placesApi.get(s.placeId)
  return { name: d.summary.name, placeId: s.placeId, lat: d.summary.lat, lon: d.summary.lon }
}

/** Origin or destination: shows the current choice and lets the user search another. */
function EndPicker({
  title,
  value,
  onPick,
}: {
  title: string
  value: Waypoint
  onPick: (w: Waypoint) => void
}) {
  const [q, setQ] = useState('')
  return (
    <div>
      <span className="font-medium">{title}</span>
      <Autocomplete
        value={q}
        onChange={setQ}
        onPick={(s) =>
          void resolve(s)
            .then((w) => {
              if (w) {
                onPick(w)
                setQ('')
              }
            })
            .catch(() => undefined)
        }
        destinations={CATALOGUES}
        placesOnly
        label={title}
        placeholder={value.name}
      />
      <p className="text-xs text-zinc-500">{value.name}</p>
    </div>
  )
}

export function RoadTripPage() {
  const [params] = useSearchParams()
  const demoId = params.get('demo')
  const [form, setForm] = useState<Form>(() => ({ ...DEFAULT, ...(demoId ? DEMOS[demoId] : {}) }))
  const [stopQuery, setStopQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<RoadTrip | null>(null)
  const [lastRequest, setLastRequest] = useState<RoadTripRequest | null>(null)
  const [problem, setProblem] = useState<Problem | null>(null)

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm((f) => ({ ...f, [k]: v }))
  const setStop = (i: number, w: Partial<Waypoint>) =>
    setForm((f) => ({ ...f, waypoints: f.waypoints.map((x, j) => (j === i ? { ...x, ...w } : x)) }))

  const plan = async (f = form) => {
    setLoading(true)
    setProblem(null)
    try {
      const req = toRequest(f)
      const r = await roadTripsApi.plan(req)
      setResult(r.roadTrip)
      setLastRequest(req)
    } catch (e) {
      setResult(null)
      setProblem(toProblem(e, 'Could not reach the planner.'))
    } finally {
      setLoading(false)
    }
  }

  const started = useRef(false)
  useEffect(() => {
    if (demoId && DEMOS[demoId] && !started.current) {
      started.current = true
      void plan()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const toggle = (i: string) =>
    setForm((f) => {
      const w = { ...f.weights }
      if (i in w) delete w[i]
      else w[i] = 3
      return { ...f, weights: w }
    })

  const input = 'block w-full rounded border border-zinc-300 px-1'
  const money = (k: keyof Form, text: string) => (
    <label>
      {text}
      <input
        type="number"
        min={0}
        value={form[k] as string}
        onChange={(e) => set(k, e.target.value as never)}
        className={input}
      />
    </label>
  )

  return (
    <div className="grid gap-4 lg:grid-cols-[340px_1fr]">
      <form
        className="space-y-3 self-start rounded border border-zinc-200 bg-white p-3 text-sm"
        onSubmit={(e) => {
          e.preventDefault()
          void plan()
        }}
      >
        <h1 className="text-base font-semibold">Plan a road trip</h1>
        {demoId && DEMOS[demoId] && (
          <p className="text-xs text-teal-800">Demo: Mumbai to Goa drive</p>
        )}
        <EndPicker title="From" value={form.origin} onPick={(w) => set('origin', w)} />

        <div>
          <span className="font-medium">Stops on the way (kept in this order)</span>
          <ol className="space-y-1">
            {form.waypoints.map((w, i) => (
              <li key={(w.placeId ?? w.name) + i} className="rounded bg-zinc-50 p-2">
                <div className="flex items-center justify-between">
                  <span>
                    {i + 1}. {w.name}
                  </span>
                  <button
                    type="button"
                    aria-label={`Remove stop ${w.name}`}
                    onClick={() =>
                      set(
                        'waypoints',
                        form.waypoints.filter((_, j) => j !== i),
                      )
                    }
                    className="text-xs text-zinc-500 underline"
                  >
                    remove
                  </button>
                </div>
                <label className="mr-3 text-xs">
                  <input
                    type="checkbox"
                    checked={!!w.overnight}
                    onChange={(e) => setStop(i, { overnight: e.target.checked || undefined })}
                  />{' '}
                  Stay overnight at {w.name}
                </label>
                <label className="text-xs">
                  Stop for (min){' '}
                  <input
                    type="number"
                    min={0}
                    aria-label={`Stop for minutes at ${w.name}`}
                    value={w.stayMinutes ?? ''}
                    onChange={(e) =>
                      setStop(i, { stayMinutes: Number(e.target.value) || undefined })
                    }
                    className="w-16 rounded border border-zinc-300 px-1"
                  />
                </label>
              </li>
            ))}
          </ol>
          <Autocomplete
            value={stopQuery}
            onChange={setStopQuery}
            onPick={(s) =>
              void resolve(s)
                .then((w) => {
                  if (w) {
                    setForm((f) => ({ ...f, waypoints: [...f.waypoints, w] }))
                    setStopQuery('')
                  }
                })
                .catch(() => undefined)
            }
            destinations={CATALOGUES}
            placesOnly
            label="Add a stop"
            placeholder="Add a stop on the way"
          />
        </div>

        <EndPicker title="To" value={form.destination} onPick={(w) => set('destination', w)} />

        <div className="grid grid-cols-2 gap-2">
          <label>
            Departure date
            <input
              type="date"
              value={form.departureDate}
              onChange={(e) => set('departureDate', e.target.value)}
              className={input}
            />
          </label>
          <label>
            Leave at
            <input
              type="time"
              value={form.departureTime}
              onChange={(e) => set('departureTime', e.target.value)}
              className={input}
            />
          </label>
          <label>
            Max driving h/day
            <input
              type="number"
              min={1}
              max={14}
              step={0.5}
              value={form.maxDrivingHoursPerDay}
              onChange={(e) => set('maxDrivingHoursPerDay', Number(e.target.value) || 7)}
              className={input}
            />
          </label>
          <label>
            Arrive by (optional)
            <input
              type="datetime-local"
              value={form.arriveBy}
              onChange={(e) => set('arriveBy', e.target.value)}
              className={input}
            />
          </label>
          <label>
            Vehicle
            <select
              value={form.transport}
              onChange={(e) => set('transport', e.target.value as Form['transport'])}
              className={input}
            >
              <option value="CAR">Car</option>
              <option value="SCOOTER">Scooter</option>
              <option value="TAXI">Taxi</option>
            </select>
          </label>
          <label>
            People
            <input
              type="number"
              min={1}
              value={form.partySize}
              onChange={(e) => set('partySize', Math.max(1, Number(e.target.value) || 1))}
              className={input}
            />
          </label>
        </div>

        <fieldset>
          <legend className="font-medium">Detours for</legend>
          <div className="grid grid-cols-2 gap-1">
            {INTERESTS.map((i) => (
              <label key={i} className="flex items-center gap-1">
                <input type="checkbox" checked={i in form.weights} onChange={() => toggle(i)} />
                {label(i)}
              </label>
            ))}
          </div>
          <div className="mt-1 grid grid-cols-2 gap-2">
            <label>
              Max extra min per detour
              <input
                type="number"
                min={0}
                value={form.maxDetourMinutes}
                onChange={(e) => set('maxDetourMinutes', Number(e.target.value) || 0)}
                className={input}
              />
            </label>
            <label>
              Max detours
              <input
                type="number"
                min={0}
                value={form.maxDetours}
                onChange={(e) => set('maxDetours', Number(e.target.value) || 0)}
                className={input}
              />
            </label>
          </div>
        </fieldset>

        <fieldset className="grid grid-cols-2 gap-2">
          <legend className="col-span-2 font-medium">Costs you know (optional)</legend>
          {money('mileageKmPerLitre', 'Mileage (km/l)')}
          {money('fuelPricePerLitre', 'Fuel (₹/l)')}
          {money('tollsTotal', 'Tolls total (₹)')}
          {money('accommodationPerNight', 'Stay per night (₹)')}
          {money('foodPerPersonPerDay', 'Food per person per day (₹)')}
          <p className="col-span-2 text-xs text-zinc-500">Blank costs stay unknown, never ₹0.</p>
        </fieldset>

        <button
          type="submit"
          disabled={loading}
          className="w-full rounded bg-teal-700 px-3 py-2 font-medium text-white disabled:opacity-50"
        >
          {loading ? 'Planning…' : 'Plan road trip'}
        </button>
      </form>

      <section aria-live="polite" aria-busy={loading}>
        {loading && (
          <p role="status" className="mb-2 text-sm text-zinc-600">
            Routing and splitting the drive into days…
          </p>
        )}
        {problem && <ProblemNotice problem={problem} />}
        {!result && !problem && !loading && (
          <p className="text-sm text-zinc-500">
            Set your start, stops and destination. Stops stay in your order; detours are optional
            and limited.
          </p>
        )}
        {result && lastRequest && (
          <div className="space-y-3">
            <SaveTrip
              kind="ROAD_TRIP"
              request={lastRequest}
              result={result}
              defaultTitle={`${lastRequest.origin.name} to ${lastRequest.destination.name}`}
            />
            <RoadTripView trip={result} />
          </div>
        )}
      </section>
    </div>
  )
}

const dt = (s: string) => s.replace('T', ' ').slice(0, 16)
const hours = (m: number) => `${Math.floor(m / 60)} h ${m % 60} min`

export function RoadTripView({ trip }: { trip: RoadTrip }) {
  const points = useMemo<MapPoint[]>(() => {
    const p: MapPoint[] = []
    trip.segments.forEach((s, i) => {
      s.detours.forEach((d) =>
        p.push({ id: 'd' + d.place.id, lat: d.place.lat, lon: d.place.lon, label: d.place.name }),
      )
      if (i === 0 && s.route?.geometry.length) {
        const [lon, lat] = s.route.geometry[0]
        p.push({ id: 'from', lat, lon, label: s.from })
      }
    })
    trip.days.forEach((d) => {
      if (d.overnight)
        p.push({
          id: 'n' + d.day,
          lat: d.overnight.lat,
          lon: d.overnight.lon,
          label: d.overnight.name,
          order: d.day,
        })
    })
    return p
  }, [trip])
  const line = useMemo(() => trip.segments.flatMap((s) => s.route?.geometry ?? []), [trip])

  return (
    <div className="space-y-3 text-sm">
      <div className="space-y-1 rounded border border-zinc-200 bg-white p-3">
        <p className="font-medium">
          {dt(trip.departure)} → {dt(trip.arrival)} · {hours(trip.totalDrivingMinutes)} driving ·{' '}
          {Math.round(trip.totalKm)} km · {trip.nights} night(s)
        </p>
        <ProviderNotice provider={trip.travelProvider} basis={trip.travelBasis} />
        <p className="text-zinc-500">{trip.disclaimer}</p>
        {trip.warnings.length > 0 && (
          <ul className="ml-4 list-disc text-amber-700">
            {trip.warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        )}
      </div>

      <div className="grid gap-3 xl:grid-cols-2">
        <Suspense fallback={<div className="h-80 rounded bg-zinc-100" />}>
          <PlaceMap points={points} line={line} className="h-80" />
        </Suspense>
        <section className="rounded border border-zinc-200 bg-white p-3">
          <h2 className="mb-1 font-medium">Segments and detours</h2>
          <p className="mb-1 text-xs text-zinc-500">
            {trip.detourCandidates} places near the route were considered.
          </p>
          {trip.segments.map((s) => (
            <div key={s.index} className="mb-2">
              <p>
                {s.from} → {s.to}: {hours(s.directMinutes)}, {Math.round(s.directKm)} km direct
              </p>
              <ul className="ml-4 list-disc text-zinc-700">
                {s.detours.map((d) => (
                  <li key={d.place.id}>
                    {d.place.name}: +{d.addedDriveMinutes} min driving, {d.visitMinutes} min visit ·
                    covers {d.covers.map(label).join(', ')}
                    <div className="text-xs text-zinc-500">{d.reason}</div>
                  </li>
                ))}
                {s.detours.length === 0 && <li className="text-zinc-500">No detours</li>}
              </ul>
            </div>
          ))}
        </section>
      </div>

      {trip.days.map((d) => (
        <DayCard key={d.day} day={d} />
      ))}

      <section className="rounded border border-zinc-200 bg-white p-3">
        <h2 className="mb-2 font-medium">Budget</h2>
        <BudgetTable budget={trip.budget} />
      </section>
    </div>
  )
}

const ITEM_TEXT: Record<RoadTripItem['type'], (i: RoadTripItem) => string> = {
  DEPART: (i) => `Leave ${i.name}`,
  DRIVE: (i) => `Drive ${i.minutes} min, ${i.km.toFixed(0)} km`,
  REST: (i) => `${i.name} (${i.minutes} min)`,
  MEAL: (i) => `${i.name} (${i.minutes} min)`,
  DETOUR: (i) => `Detour: ${i.name} (${i.minutes} min)`,
  WAYPOINT: (i) => `Stop: ${i.name}${i.minutes ? ` (${i.minutes} min)` : ''}`,
  OVERNIGHT: (i) => `Overnight: ${i.name}`,
  ARRIVE: (i) => `Arrive ${i.name}`,
}

function DayCard({ day }: { day: RoadTripDay }) {
  const o = day.overnight
  return (
    <section className="rounded border border-zinc-200 bg-white p-3">
      <h2 className="font-medium">
        Day {day.day} · {day.date} · {hours(day.drivingMinutes)} driving, {Math.round(day.km)} km ·
        longest stretch {day.longestContinuousDrivingMinutes} min
      </h2>
      <ol aria-label={`Day ${day.day} timeline`} className="mt-1 space-y-0.5">
        {day.items.map((i, k) => (
          <li key={k} className={i.type === 'DRIVE' ? 'text-xs text-zinc-500' : ''}>
            <span className="mr-2 font-mono text-xs">{i.start.slice(11, 16)}</span>
            {ITEM_TEXT[i.type](i)}
            {i.note && <span className="text-xs text-zinc-500"> · {i.note}</span>}
          </li>
        ))}
      </ol>
      {o && (
        <p className="mt-1">
          {o.kind === 'SUGGESTED' ? 'Suggested stay' : 'Your stay'}: {o.name}{' '}
          <ConfidenceBadge confidence={o.confidence as Confidence} />{' '}
          <span className="text-xs text-zinc-500">{o.note}</span>
        </p>
      )}
      {day.navigationUrls.map((u, k) => (
        <a
          key={u}
          href={u}
          target="_blank"
          rel="noreferrer"
          className="mr-3 text-teal-700 underline"
        >
          Open day {day.day} in Google Maps{day.navigationUrls.length > 1 ? ` (part ${k + 1})` : ''}
        </a>
      ))}
    </section>
  )
}
