import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { placesApi } from '../../api/places'
import { roadTripsApi } from '../../api/roadTrips'
import { BudgetTable } from '../../components/BudgetTable'
import { ConfidenceBadge } from '../../components/ConfidenceBadge'
import { PrintButton } from '../../components/PrintSheet'
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
  RoadTripStay,
  ReturnTrip,
  Waypoint,
} from '../../types/roadtrip'
import { label } from '../../utils/format'
import { Autocomplete } from '../discover/Autocomplete'
import { ItineraryView } from '../plan/ItineraryView'
import { hhmm, hours, ITEM_TEXT, stayKind } from './format'
import { RoadTripPrint } from './RoadTripPrint'
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
  drivingDays: string
  stayDays: string
  planReturn: boolean
  /** The way home picked from the last plan, sent again so a re-plan keeps it. */
  returnChoice: number
  stayChoices: Record<string, string>
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
  drivingDays: '',
  stayDays: '',
  planReturn: false,
  returnChoice: 1,
  stayChoices: {},
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
    stayDays: '2',
    planReturn: true,
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
    drivingDays: num(f.drivingDays),
    stayDays: num(f.stayDays),
    planReturn: f.planReturn || undefined,
    returnChoice: f.planReturn ? f.returnChoice : undefined,
    stayChoices: f.stayChoices,
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
  if (s.type === 'TOWN' && s.lat != null && s.lon != null)
    return { name: s.label, lat: s.lat, lon: s.lon }
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
        towns
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

  const input = 'field'
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
    <div className="grid gap-5 lg:grid-cols-[340px_minmax(0,1fr)] [&>*]:min-w-0">
      <form
        className="space-y-3 self-start card p-3 text-sm"
        onSubmit={(e) => {
          e.preventDefault()
          void plan()
        }}
      >
        <h1 className="text-xl font-bold">Plan a road trip</h1>
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
                    className="field w-16 px-1.5"
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
            towns
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
            Driving days (optional)
            <input
              type="number"
              min={1}
              max={14}
              value={form.drivingDays}
              onChange={(e) =>
                setForm((f) => ({ ...f, drivingDays: e.target.value, stayChoices: {} }))
              }
              className={input}
            />
            <span className="text-xs text-zinc-500">
              2 or more: suggests towns to stay in between
            </span>
          </label>
          <label>
            Days at destination (optional)
            <input
              type="number"
              min={0}
              max={14}
              value={form.stayDays}
              onChange={(e) => set('stayDays', e.target.value)}
              className={input}
            />
            <span className="text-xs text-zinc-500">Plans your days there, like the Plan page</span>
          </label>
          <label className="col-span-2">
            <input
              type="checkbox"
              checked={form.planReturn}
              onChange={(e) => set('planReturn', e.target.checked)}
            />{' '}
            Plan the way home
            <span className="block text-xs text-zinc-500">Four ways back after the stay</span>
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
          <div className="mt-1 grid grid-cols-2 items-end gap-2">
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

        <button type="submit" disabled={loading} className="w-full btn-primary">
          {loading ? 'Planning…' : 'Plan road trip'}
        </button>
      </form>

      <section aria-live="polite" aria-busy={loading}>
        {loading && (
          <p role="status" className="mb-3 flex items-center gap-2 text-sm text-zinc-600">
            <span aria-hidden className="size-2 animate-pulse rounded-full bg-teal-600" />
            Routing and splitting the drive into days…
          </p>
        )}
        {loading && !result && (
          <div aria-hidden className="space-y-3">
            <div className="skeleton h-28" />
            <div className="grid gap-3 lg:grid-cols-2">
              <div className="skeleton h-72" />
              <div className="skeleton h-72" />
            </div>
            <div className="skeleton h-40" />
          </div>
        )}
        {problem && <ProblemNotice problem={problem} />}
        {!result && !problem && !loading && (
          <p className="rounded-lg border border-dashed border-zinc-300 p-8 text-center text-sm text-zinc-500">
            Set your start, stops and destination. Stops stay in your order; detours are optional
            and limited.
          </p>
        )}
        {result && lastRequest && (
          <div
            className={`space-y-4 transition-opacity duration-200 ${loading ? 'opacity-60' : ''}`}
          >
            <SaveTrip
              kind="ROAD_TRIP"
              request={lastRequest}
              result={result}
              defaultTitle={`${lastRequest.origin.name} to ${lastRequest.destination.name}`}
            />
            <RoadTripView
              trip={result}
              onPickStay={(night, key) => {
                const f = { ...form, stayChoices: { ...form.stayChoices, [night]: key } }
                setForm(f)
                void plan(f)
              }}
              onPickReturn={(n) => {
                // All four are already planned: switch without a request, and keep the pick for saving.
                setLastRequest({ ...lastRequest, returnChoice: n })
                setForm((f) => ({ ...f, returnChoice: n }))
                setResult({ ...result, returnTrip: { ...result.returnTrip!, chosen: n } })
              }}
            />
          </div>
        )}
      </section>
    </div>
  )
}

const dt = (s: string) => s.replace('T', ' ').slice(0, 16)

export function RoadTripView({
  trip,
  onPickStay,
  onPickReturn,
  title = trip.segments.length
    ? `Road trip: ${trip.segments[0].from} to ${trip.segments.at(-1)!.to}`
    : 'Road trip',
}: {
  trip: RoadTrip
  /** Heading and PDF file name of the printable copy. */
  title?: string
  /** Re-plans with this town as the night's stay; absent where the trip is read-only. */
  onPickStay?: (night: number, townKey: string) => void
  /** Shows another way home (already planned); absent where the trip is read-only. */
  onPickReturn?: (n: number) => void
}) {
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
      <RoadTripPrint trip={trip} title={title} />
      <div className="space-y-1 card p-3">
        <div className="flex items-start justify-between gap-2">
          <p className="font-medium">
            {dt(trip.departure)} → {dt(trip.arrival)} · {hours(trip.totalDrivingMinutes)} driving ·{' '}
            {Math.round(trip.totalKm)} km · {trip.nights} night(s)
          </p>
          <PrintButton />
        </div>
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
        <section className="card p-3">
          <h2 className="mb-2 text-base font-semibold">Segments and detours</h2>
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

      {!!trip.stayOptions?.length && (
        <section className="card p-3">
          <h2 className="mb-2 text-base font-semibold">Where to stay on the way</h2>
          <p className="mb-2 text-xs text-zinc-500">
            Towns with places to stay, ranked by closeness to an even split of the drive, the extra
            driving to reach them, and how much there is to do. Places to stay are OpenStreetMap
            listings: availability and price unknown.
          </p>
          <p className="mb-2 text-xs text-zinc-500">
            <span className="inline-block rounded-full bg-sky-100 px-2 py-0.5 text-[11px] font-semibold text-sky-800">
              Detour
            </span>{' '}
            rates the extra driving to leave the route for the town: 5/5 is on the route (under 15
            min), one point less per extra 15 min, 1/5 at the 60 min limit.{' '}
            <span className="inline-block rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-800">
              Interests
            </span>{' '}
            rates the things to do within 15 km that match your interests (all of them when none are
            ticked): one point per 4 places, 5/5 at 20 or more, 0/5 when none match.
          </p>
          {trip.stayOptions.map((n) => (
            <div key={n.night} className="mb-2">
              <p>
                Night {n.night} · aim: {hours(n.targetDriveMinutes)} of driving from the start
              </p>
              <ul className="ml-4 space-y-2">
                {n.options.map((o) => (
                  <li key={o.key}>
                    <span className={o.chosen ? 'font-medium' : ''}>{o.town}</span>
                    {!!o.detourRating && (
                      <>
                        {' '}
                        <span
                          className="inline-block rounded-full bg-sky-100 px-2 py-0.5 text-[11px] font-semibold text-sky-800"
                          title={`Detour ${o.detourRating}/5: ${o.detourMinutes} min of extra driving (5 = on the route)`}
                        >
                          Detour {o.detourRating}/5
                        </span>{' '}
                        <span
                          className="inline-block rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-800"
                          title={`Interests ${o.interestRating ?? 0}/5: ${o.interestMatches ?? 0} matching thing(s) to do within 15 km (5 = 20 or more)`}
                        >
                          Interests {o.interestRating ?? 0}/5
                        </span>
                      </>
                    )}
                    : {hours(o.driveFromStartMinutes)} from the start
                    {o.detourMinutes > 0 && `, +${o.detourMinutes} min off the route`}
                    <div className="text-xs text-zinc-600">
                      {o.sights} thing(s) to do within 15 km
                      {o.topSights.length > 0 && `: ${o.topSights.join(', ')}`}
                    </div>
                    <div className="text-xs text-zinc-600">
                      {o.stays} place(s) to stay within 5 km: {o.stayNames.join(', ')}
                    </div>
                    {o.chosen ? (
                      <span className="ml-2 text-xs text-teal-700">chosen</span>
                    ) : (
                      onPickStay && (
                        <button
                          type="button"
                          onClick={() => onPickStay(n.night, o.key)}
                          className="btn-sm ml-2"
                        >
                          Stay here
                        </button>
                      )
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </section>
      )}

      {trip.days.map((d) => (
        <DayCard key={d.day} day={d} />
      ))}

      {trip.stay && <StaySection trip={trip} stay={trip.stay} />}
      {trip.returnTrip && <ReturnSection rt={trip.returnTrip} onPick={onPickReturn} />}

      <section className="card p-3">
        <h2 className="mb-3 text-base font-semibold">Budget</h2>
        <BudgetTable budget={trip.budget} />
      </section>
    </div>
  )
}

/** The days at the destination, planned like the Plan page; read-only. */
function StaySection({ trip, stay }: { trip: RoadTrip; stay: RoadTripStay }) {
  const arrive = trip.days.at(-1)?.items.find((i) => i.type === 'ARRIVE')
  const place = trip.segments.at(-1)?.to ?? arrive?.name ?? 'the destination'
  return (
    <section className="space-y-2">
      <div className="card p-3">
        <h2 className="text-base font-semibold">
          Your stay in {place} · {stay.firstDay} to {stay.lastDay}
        </h2>
        <p>
          {stay.nights} night(s) at {place}
          {' · priced in the trip budget above'}
        </p>
        {stay.note && <p className="text-amber-700">{stay.note}</p>}
      </div>
      {stay.itinerary && arrive?.lat != null && arrive.lon != null && (
        <ItineraryView
          it={stay.itinerary}
          base={{ lat: arrive.lat, lon: arrive.lon }}
          printable={false}
        />
      )}
    </section>
  )
}

/** Rail node colour: stays in clay, breaks muted, meals amber, places in brand indigo. */
const NODE: Partial<Record<RoadTripItem['type'], string>> = {
  OVERNIGHT: 'bg-clay-500',
  REST: 'bg-zinc-400',
  MEAL: 'bg-amber-400',
}

/** The four ways home as choices, then the chosen one's days. Without onPick the choice is read-only. */
function ReturnSection({ rt, onPick }: { rt: ReturnTrip; onPick?: (n: number) => void }) {
  const chosen = rt.options.find((o) => o.number === rt.chosen)?.plan
  return (
    <section className="space-y-2">
      <fieldset className="card space-y-2 p-3">
        <legend className="sr-only">Way home</legend>
        <h2 className="text-base font-semibold">Way home</h2>
        {rt.options.map((o) => {
          const p = o.plan
          const summary = p
            ? `${hours(p.totalDrivingMinutes)} · ${Math.round(p.totalKm)} km · ${p.nights} night(s) · ${p.days.flatMap((d) => d.items).filter((i) => i.type === 'DETOUR' || i.type === 'WAYPOINT').length} stop(s)`
            : ''
          return (
            <label
              key={o.number}
              className={`flex items-start gap-2 rounded p-2 ${o.number === rt.chosen ? 'bg-teal-50' : ''} ${p ? '' : 'text-zinc-500'}`}
            >
              <input
                type="radio"
                name="way-home"
                aria-label={`${o.number}. ${o.label}`}
                checked={o.number === rt.chosen}
                disabled={!p || !onPick}
                onChange={() => onPick?.(o.number)}
                className="mt-1"
              />
              <span>
                <span className="font-medium">
                  {o.number}. {o.label}
                </span>
                {summary && <span className="block text-xs text-zinc-600">{summary}</span>}
                {o.note && <span className="block text-xs text-amber-700">{o.note}</span>}
              </span>
            </label>
          )
        })}
      </fieldset>
      {chosen?.days.map((d) => (
        <DayCard key={'r' + d.day} day={d} label="Return day" />
      ))}
      {chosen && (
        <section className="card p-3">
          <h2 className="mb-3 text-base font-semibold">Budget for the way home</h2>
          <BudgetTable budget={chosen.budget} />
        </section>
      )}
    </section>
  )
}

function DayCard({ day, label = 'Day' }: { day: RoadTripDay; label?: string }) {
  const o = day.overnight
  return (
    <section className="card p-3">
      <h2 className="text-base font-semibold">
        {label} {day.day} · {day.date} · {hours(day.drivingMinutes)} driving, {Math.round(day.km)}{' '}
        km · longest stretch {day.longestContinuousDrivingMinutes} min
      </h2>
      {/* Route rail: drives are legs on the line, everything else is a stop on it. */}
      <ol
        aria-label={`Day ${day.day} timeline`}
        className="reveal relative mt-3 space-y-1.5 before:absolute before:top-2 before:bottom-2 before:left-[5px] before:w-0.5 before:rounded-full before:bg-teal-100"
      >
        {day.items.map((i, k) => (
          <li
            key={k}
            style={{ '--i': k } as React.CSSProperties}
            className={`relative pl-6 ${i.type === 'DRIVE' ? 'text-xs text-zinc-500' : 'text-sm'}`}
          >
            {i.type !== 'DRIVE' && (
              <span
                aria-hidden
                className={`absolute top-1.5 left-0 size-3 rounded-full ring-4 ring-white ${NODE[i.type] ?? 'bg-teal-700'}`}
              />
            )}
            <span className="tabular mr-2 text-xs font-medium text-zinc-500">{hhmm(i.start)}</span>
            {ITEM_TEXT[i.type](i)}
            {i.note && <span className="text-xs text-zinc-500"> · {i.note}</span>}
          </li>
        ))}
      </ol>
      {o && (
        <p className="mt-1">
          {stayKind(o)}: {o.name} <ConfidenceBadge confidence={o.confidence as Confidence} />{' '}
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
