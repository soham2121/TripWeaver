import { lazy, Suspense, useMemo, useState } from 'react'
import { ApiError } from '../../api/client'
import { itinerariesApi } from '../../api/itineraries'
import { placesApi } from '../../api/places'
import type { MapPoint } from '../../maps/PlaceMap'
import type { Day, Itinerary, Pace, Transport } from '../../types/itinerary'
import { INTERESTS, type Suggestion } from '../../types/place'
import { label } from '../../utils/format'
import { Autocomplete } from '../discover/Autocomplete'

const PlaceMap = lazy(() => import('../../maps/PlaceMap'))

/** Default base: Candolim, North Goa (the demo accommodation area). */
const DEFAULT_BASE = { name: 'Candolim (default)', lat: 15.5202, lon: 73.7659 }

const hhmm = (t: string) => t.slice(0, 5)

interface Failure {
  message: string
  conflicts: string[]
  relaxations: string[]
}

export function PlanPage() {
  const [weights, setWeights] = useState<Record<string, number>>({})
  const [days, setDays] = useState(2)
  const [startDate, setStartDate] = useState('')
  const [pace, setPace] = useState<Pace>('BALANCED')
  const [transport, setTransport] = useState<Transport>('CAR')
  const [base, setBase] = useState(DEFAULT_BASE)
  const [baseQuery, setBaseQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<Itinerary | null>(null)
  const [failure, setFailure] = useState<Failure | null>(null)
  const [dayIdx, setDayIdx] = useState(0)

  const toggle = (i: string) =>
    setWeights((w) => {
      const n = { ...w }
      if (i in n) delete n[i]
      else n[i] = 3
      return n
    })

  const pickBase = async (s: Suggestion) => {
    if (s.type !== 'PLACE' || !s.placeId) return
    try {
      const d = await placesApi.get(s.placeId)
      setBase({ name: d.summary.name, lat: d.summary.lat, lon: d.summary.lon })
      setBaseQuery(d.summary.name)
    } catch {
      /* keep previous base */
    }
  }

  const generate = async () => {
    setLoading(true)
    setFailure(null)
    try {
      const r = await itinerariesApi.generate({
        baseLat: base.lat,
        baseLon: base.lon,
        baseName: base.name,
        startDate: startDate || undefined,
        days,
        partySize: 2,
        interests: weights,
        pace,
        transport,
      })
      setResult(r.itinerary)
      setDayIdx(0)
    } catch (e) {
      setResult(null)
      if (e instanceof ApiError)
        setFailure({
          message: e.message,
          conflicts: (e.details.conflicts as string[]) ?? [],
          relaxations: (e.details.suggestedRelaxations as string[]) ?? [],
        })
      else setFailure({ message: 'Could not reach the planner.', conflicts: [], relaxations: [] })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[320px_1fr]">
      <form
        className="space-y-3 rounded border border-zinc-200 bg-white p-3 text-sm"
        onSubmit={(e) => {
          e.preventDefault()
          void generate()
        }}
      >
        <fieldset>
          <legend className="font-medium">Interests</legend>
          <div className="mt-1 grid grid-cols-2 gap-1">
            {INTERESTS.map((i) => (
              <div key={i} className="flex items-center gap-1">
                <label className="flex flex-1 items-center gap-1">
                  <input type="checkbox" checked={i in weights} onChange={() => toggle(i)} />
                  {label(i)}
                </label>
                {i in weights && (
                  <select
                    aria-label={`${label(i)} importance`}
                    value={weights[i]}
                    onChange={(e) => setWeights((w) => ({ ...w, [i]: Number(e.target.value) }))}
                    className="rounded border border-zinc-300 text-xs"
                  >
                    {[1, 2, 3, 4, 5].map((n) => (
                      <option key={n}>{n}</option>
                    ))}
                  </select>
                )}
              </div>
            ))}
          </div>
        </fieldset>
        <div>
          <span className="font-medium">Stay near</span>
          <Autocomplete
            value={baseQuery}
            onChange={setBaseQuery}
            onPick={(s) => void pickBase(s)}
            placeholder="Search a place for your base"
          />
          <p className="text-xs text-zinc-500">Base: {base.name}</p>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <label>
            Start date
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="block w-full rounded border border-zinc-300 px-1"
            />
          </label>
          <label>
            Days
            <input
              type="number"
              min={1}
              max={14}
              value={days}
              onChange={(e) => setDays(Math.max(1, Math.min(14, Number(e.target.value) || 1)))}
              className="block w-full rounded border border-zinc-300 px-1"
            />
          </label>
          <label>
            Pace
            <select
              value={pace}
              onChange={(e) => setPace(e.target.value as Pace)}
              className="block w-full rounded border border-zinc-300"
            >
              <option value="RELAXED">Relaxed</option>
              <option value="BALANCED">Balanced</option>
              <option value="PACKED">Packed</option>
            </select>
          </label>
          <label>
            Transport
            <select
              value={transport}
              onChange={(e) => setTransport(e.target.value as Transport)}
              className="block w-full rounded border border-zinc-300"
            >
              <option value="CAR">Car</option>
              <option value="SCOOTER">Scooter</option>
              <option value="TAXI">Taxi</option>
              <option value="WALK">Walk</option>
            </select>
          </label>
        </div>
        <button
          type="submit"
          disabled={loading || Object.keys(weights).length === 0}
          className="w-full rounded bg-teal-700 px-3 py-2 font-medium text-white disabled:opacity-50"
        >
          {loading ? 'Planning…' : 'Generate plan'}
        </button>
      </form>

      <section aria-live="polite">
        {failure && (
          <div role="alert" className="rounded border border-amber-300 bg-amber-50 p-3 text-sm">
            <p className="font-medium">{failure.message}</p>
            <ul className="ml-4 list-disc">
              {failure.conflicts.map((c) => (
                <li key={c}>{c}</li>
              ))}
            </ul>
            {failure.relaxations.length > 0 && (
              <>
                <p className="mt-2 font-medium">Try:</p>
                <ul className="ml-4 list-disc">
                  {failure.relaxations.map((c) => (
                    <li key={c}>{c}</li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}
        {!result && !failure && (
          <p className="text-sm text-zinc-500">Choose interests and generate a day-by-day plan.</p>
        )}
        {result && (
          <Result
            it={result}
            dayIdx={dayIdx}
            setDayIdx={setDayIdx}
            base={{ lat: base.lat, lon: base.lon }}
          />
        )}
      </section>
    </div>
  )
}

function Result({
  it,
  dayIdx,
  setDayIdx,
  base,
}: {
  it: Itinerary
  dayIdx: number
  setDayIdx: (i: number) => void
  base: { lat: number; lon: number }
}) {
  const day = it.days[dayIdx]
  return (
    <div className="space-y-3 text-sm">
      <div className="rounded border border-zinc-200 bg-white p-3">
        <p>
          <span className="font-medium">
            {it.travelProvider === 'OSRM'
              ? 'Road routing: OSRM'
              : 'Travel times: straight-line estimate'}
          </span>{' '}
          <span className="text-zinc-500">— {it.travelBasis}</span>
        </p>
        <p className="text-zinc-500">{it.disclaimer}</p>
        <p>
          Budget: {label(it.budget.status)} · known up to ₹{Math.round(it.budget.knownMax)}
          {it.budget.unknownItems.length > 0 &&
            ` · ${it.budget.unknownItems.length} unknown item(s)`}
        </p>
        {it.coverage.uncovered.length > 0 && (
          <p className="text-amber-700">
            Not covered:{' '}
            {it.coverage.uncovered.map((u) => `${label(u)} (${it.coverage.reasons[u]})`).join('; ')}
          </p>
        )}
        {it.warnings.length > 0 && (
          <ul className="ml-4 list-disc text-zinc-600">
            {it.warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        )}
      </div>
      <div role="tablist" className="flex flex-wrap gap-1">
        {it.days.map((d, i) => (
          <button
            key={d.day}
            role="tab"
            aria-selected={i === dayIdx}
            onClick={() => setDayIdx(i)}
            className={`rounded px-3 py-1 ${i === dayIdx ? 'bg-teal-700 text-white' : 'bg-white ring-1 ring-zinc-200'}`}
          >
            Day {d.day} · {label(d.dayOfWeek)}
          </button>
        ))}
      </div>
      {day && <DayView day={day} base={base} />}
    </div>
  )
}

function DayView({ day, base }: { day: Day; base: { lat: number; lon: number } }) {
  const points = useMemo<MapPoint[]>(
    () => [
      { id: 'base', lat: base.lat, lon: base.lon, label: 'Base' },
      ...day.stops.map((s) => ({
        id: s.place.id,
        lat: s.place.lat,
        lon: s.place.lon,
        label: s.place.name,
        order: s.order,
      })),
    ],
    [day, base],
  )
  const o = day.ordering
  return (
    <div className="grid gap-3 xl:grid-cols-2">
      <div className="space-y-2">
        <Suspense fallback={<div className="h-80 rounded bg-zinc-100" />}>
          <PlaceMap points={points} line={day.route?.geometry} className="h-80" />
        </Suspense>
        {day.route && (
          <p className="text-xs text-zinc-500">
            Route: {day.route.totalKm.toFixed(1)} km, {day.route.totalMinutes} min driving (
            {day.route.provider === 'OSRM' ? 'road network' : 'straight lines, estimate'})
          </p>
        )}
        {day.navigationUrls.map((u, i) => (
          <a
            key={u}
            href={u}
            target="_blank"
            rel="noreferrer"
            className="mr-3 text-teal-700 underline"
          >
            Open in Google Maps{day.navigationUrls.length > 1 ? ` (part ${i + 1})` : ''}
          </a>
        ))}
      </div>
      <div className="space-y-2 rounded border border-zinc-200 bg-white p-3">
        <p>
          {hhmm(day.start)}–{hhmm(day.end)} · {label(day.pace)} pace · workload{' '}
          {label(day.workload.label)} ({Math.round(day.workload.utilisation * 100)}% busy,{' '}
          {day.workload.freeMinutes} min free) · back at base {hhmm(day.endOfDay)}
        </p>
        <ol aria-label={`Day ${day.day} stops`} className="space-y-1">
          {day.stops.map((s) => (
            <li key={s.place.id} className="rounded bg-zinc-50 p-2">
              <span className="font-medium">
                {s.order}. {s.place.name}
              </span>{' '}
              <span className="text-zinc-600">
                {hhmm(s.start)}–{hhmm(s.end)} · {s.travelMinutes} min travel
                {s.waitMinutes > 0 && ` · waits ${s.waitMinutes} min`} ·{' '}
                {s.cost.unknown ? 'cost unknown' : `₹${s.cost.max}`} · hours{' '}
                {s.hours.status.toLowerCase()}
              </span>
              <div className="text-xs text-zinc-500">{s.reason}</div>
            </li>
          ))}
        </ol>
        {day.meals.map((m) => (
          <p key={m.label} className="text-zinc-600">
            {m.label} {hhmm(m.start)}–{hhmm(m.end)}
            {m.place ? ` at ${m.place}` : ' (break)'}
          </p>
        ))}
        {o && o.doubleTreeCost != null && (
          <p className="text-xs text-zinc-600">
            Order: {o.applied === 'DOUBLE_TREE' ? 'Double-Tree tour' : 'insertion order'}.{' '}
            Double-Tree {o.doubleTreeCost} min
            {o.guaranteeApplies ? ' (≤ 2 × optimal on symmetric travel times)' : ''}
            {o.exactCost != null && `, exact optimum ${o.exactCost} min`}
            {o.nearestNeighbourCost != null && `, nearest-neighbour ${o.nearestNeighbourCost} min`},
            insertion {o.insertionCost} min. {o.note}
          </p>
        )}
        {day.warnings.map((w) => (
          <p key={w} className="text-amber-700">
            {w}
          </p>
        ))}
      </div>
    </div>
  )
}
