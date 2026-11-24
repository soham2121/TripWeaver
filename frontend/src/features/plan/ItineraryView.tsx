import { lazy, Suspense, useMemo, useState } from 'react'
import { BudgetTable } from '../../components/BudgetTable'
import { ConfidenceBadge } from '../../components/ConfidenceBadge'
import { ProviderNotice } from '../../components/ProblemNotice'
import type { MapPoint } from '../../maps/PlaceMap'
import type { Day, Itinerary, Stop, Workload } from '../../types/itinerary'
import type { Confidence } from '../../types/place'
import { label } from '../../utils/format'
import { Autocomplete } from '../discover/Autocomplete'
import type { Edit } from './edits'

const PlaceMap = lazy(() => import('../../maps/PlaceMap'))

const hhmm = (t: string) => t.slice(0, 5)

interface StopActions {
  locked: Set<string>
  busy: boolean
  toggleLock: (s: Stop) => void
  edit: (e: Edit) => void
}

/** A generated itinerary. Without onEdit it is read-only (viewers, shared links). */
export function ItineraryView({
  it,
  base,
  onEdit,
  busy = false,
}: {
  it: Itinerary
  base: { lat: number; lon: number }
  onEdit?: (e: Edit) => void
  busy?: boolean
}) {
  const [dayIdx, setDayIdx] = useState(0)
  const [mustQuery, setMustQuery] = useState('')
  const [locked, setLocked] = useState<Set<string>>(new Set())
  const actions: StopActions | undefined = onEdit && {
    locked,
    busy,
    edit: onEdit,
    toggleLock: (s) =>
      setLocked((l) => {
        const n = new Set(l)
        if (n.has(s.place.id)) n.delete(s.place.id)
        else n.add(s.place.id)
        return n
      }),
  }
  const day = it.days[Math.min(dayIdx, it.days.length - 1)]
  return (
    <div className="space-y-3 text-sm">
      <div className="space-y-1 card p-3">
        <ProviderNotice provider={it.travelProvider} basis={it.travelBasis} />
        <p className="text-zinc-500">{it.disclaimer}</p>
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
        {it.repairs.length + it.unscheduled.length > 0 && (
          <details>
            <summary className="cursor-pointer text-zinc-600">
              Left out or changed ({it.repairs.length + it.unscheduled.length})
            </summary>
            <ul className="ml-4 list-disc text-zinc-600">
              {it.repairs.map((r) => (
                <li key={'r' + r.placeId + r.action}>
                  {r.name}: {r.reason}
                </li>
              ))}
              {it.unscheduled.map((u) => (
                <li key={'u' + u.placeId}>
                  {u.name}: {u.reason}
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>

      <div className="space-y-3">
        <section className="card p-3">
          <h2 className="mb-3 text-base font-semibold">Overview and workload</h2>
          <ul className="space-y-2">
            {it.days.map((d) => (
              <li key={d.day}>
                <div className="flex justify-between text-xs">
                  <span>
                    Day {d.day} · {label(d.dayOfWeek.toLowerCase())} {d.date} · {d.stops.length}{' '}
                    stop(s)
                  </span>
                  <span>
                    {label(d.workload.label.toLowerCase())} ·{' '}
                    {Math.round(d.workload.utilisation * 100)}% busy
                  </span>
                </div>
                <WorkloadBar day={d.day} w={d.workload} />
              </li>
            ))}
          </ul>
          <p className="mt-2 flex flex-wrap gap-3 text-xs text-zinc-500">
            {LEGEND.map(([k, cls]) => (
              <span key={k}>
                <span className={`mr-1 inline-block h-2 w-2 rounded-sm ${cls}`} />
                {k}
              </span>
            ))}
          </p>
        </section>
        <section className="card p-3">
          <h2 className="mb-3 text-base font-semibold">Budget</h2>
          <BudgetTable budget={it.budget} />
        </section>
      </div>

      {actions && (
        <section className="flex flex-wrap items-end gap-2 card p-3">
          <div className="min-w-64 flex-1">
            <span className="text-xs text-zinc-600">Add a must-visit place</span>
            <Autocomplete
              value={mustQuery}
              onChange={setMustQuery}
              onPick={(s) => {
                setMustQuery('')
                if (s.type === 'PLACE' && s.placeId)
                  actions.edit({ kind: 'must', placeId: s.placeId })
              }}
              placesOnly
              label="Add a must-visit place"
              placeholder="Search a place to include"
            />
          </div>
          <button
            type="button"
            disabled={actions.busy}
            onClick={() => actions.edit({ kind: 'replan', locked: [...locked] })}
            className="btn-primary"
          >
            Replan, keeping locked stops
          </button>
          <p className="w-full text-xs text-zinc-500">
            Lock stops you want to keep. Remove takes a stop out without a replacement; replace asks
            for another suggestion. Every edit re-runs the planner, so times update.
          </p>
        </section>
      )}

      <div role="tablist" className="flex flex-wrap gap-1">
        {it.days.map((d, i) => (
          <button
            key={d.day}
            role="tab"
            aria-selected={d === day}
            onClick={() => setDayIdx(i)}
            className={`rounded-full px-3.5 py-1 text-sm font-medium ${d === day ? 'bg-teal-700 text-white shadow-sm' : 'bg-white text-zinc-700 ring-1 ring-zinc-200 hover:ring-zinc-300'}`}
          >
            Day {d.day} · {label(d.dayOfWeek.toLowerCase())}
          </button>
        ))}
      </div>
      {day && <DayView day={day} days={it.days.length} base={base} actions={actions} />}
    </div>
  )
}

const LEGEND: [string, string][] = [
  ['Visits', 'bg-teal-600'],
  ['Travel', 'bg-sky-500'],
  ['Meals', 'bg-amber-400'],
  ['Buffer and waits', 'bg-zinc-400'],
  ['Free', 'bg-zinc-100 ring-1 ring-zinc-300'],
]

function WorkloadBar({ day, w }: { day: number; w: Workload }) {
  const parts: [number, string][] = [
    [w.activityMinutes, LEGEND[0][1]],
    [w.travelMinutes, LEGEND[1][1]],
    [w.mealMinutes, LEGEND[2][1]],
    [w.bufferMinutes + w.waitMinutes, LEGEND[3][1]],
  ]
  const pct = Math.round(w.utilisation * 100)
  return (
    <div
      role="meter"
      aria-label={`Day ${day} workload`}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={pct}
      aria-valuetext={`${pct}% busy, ${w.freeMinutes} min free`}
      className="mt-1 flex h-3 overflow-hidden rounded bg-zinc-100"
    >
      {parts.map(([m, cls], i) =>
        m > 0 ? (
          <span key={i} className={cls} style={{ width: `${(100 * m) / w.windowMinutes}%` }} />
        ) : null,
      )}
    </div>
  )
}

type Entry = { at: string; stop?: Stop; meal?: Day['meals'][number] }

function DayView({
  day,
  days,
  base,
  actions,
}: {
  day: Day
  days: number
  base: { lat: number; lon: number }
  actions?: StopActions
}) {
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
  // Times before the day's start are after midnight on a day that ends late; sort them last.
  const afterStart = (t: string) => (t < day.start ? '1' : '0') + t
  const entries: Entry[] = [
    ...day.stops.map((s) => ({ at: s.start, stop: s })),
    ...day.meals.map((m) => ({ at: m.start, meal: m })),
  ].sort((a, b) => afterStart(a.at).localeCompare(afterStart(b.at)))
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
      </div>
      <div className="card space-y-3 p-4">
        <p>
          {hhmm(day.start)}–{hhmm(day.end)} · {label(day.pace.toLowerCase())} pace ·{' '}
          {day.workload.freeMinutes} min free ·{' '}
          {day.returnToBase ? `back at base ${hhmm(day.endOfDay)}` : `ends ${hhmm(day.endOfDay)}`}
        </p>
        {/* Route rail: one line joins the day's stops; entries cascade in when a plan arrives. */}
        <ol
          aria-label={`Day ${day.day} stops`}
          className="reveal relative space-y-2 before:absolute before:top-3 before:bottom-3 before:left-[11px] before:w-0.5 before:rounded-full before:bg-teal-100"
        >
          {entries.map((e, i) =>
            e.stop ? (
              <StopItem
                key={e.stop.place.id}
                s={e.stop}
                day={day.day}
                days={days}
                actions={actions}
                index={i}
              />
            ) : (
              <li
                key={'meal' + e.meal!.label}
                style={{ '--i': i } as React.CSSProperties}
                className="relative pl-8 text-sm text-zinc-700"
              >
                <span
                  aria-hidden
                  className="absolute top-1.5 left-[7px] size-2.5 rounded-full bg-amber-400 ring-4 ring-white"
                />
                <span className="tabular">
                  {e.meal!.label} {hhmm(e.meal!.start)}–{hhmm(e.meal!.end)}
                </span>
                <span className="text-zinc-500">
                  {e.meal!.place ? ` at ${e.meal!.place}` : ' (break)'}
                </span>
              </li>
            ),
          )}
          {day.returnToBase && (
            <li
              style={{ '--i': entries.length } as React.CSSProperties}
              className="relative pl-8 text-xs text-zinc-500"
            >
              <span
                aria-hidden
                className="absolute top-0.5 left-[6px] size-3 rounded-full border-2 border-teal-700 bg-white"
              />
              {day.returnTravelMinutes} min back to base
            </li>
          )}
        </ol>
        {day.warnings.map((w) => (
          <p key={w} className="text-amber-700">
            {w}
          </p>
        ))}
      </div>
    </div>
  )
}

const btn = 'btn-sm'

function StopItem({
  s,
  day,
  days,
  actions,
  index = 0,
}: {
  s: Stop
  day: number
  days: number
  actions?: StopActions
  index?: number
}) {
  const name = s.place.name
  const locked = actions?.locked.has(s.place.id) ?? false
  return (
    <li style={{ '--i': index } as React.CSSProperties} className="relative pl-8">
      <span
        aria-hidden
        className={`absolute top-7 left-[5px] size-3.5 rounded-full ring-4 ring-white ${locked ? 'bg-clay-500' : 'bg-teal-700'}`}
      />
      <div className="pb-1 text-xs text-zinc-500 tabular">
        {s.travelMinutes} min, {s.travelKm.toFixed(1)} km
        {s.waitMinutes > 0 && ` · waits ${s.waitMinutes} min for opening`}
      </div>
      <div
        className={`rounded-lg border p-3 transition-colors ${locked ? 'border-clay-100 bg-clay-50' : 'border-zinc-200 bg-white hover:border-zinc-300'}`}
      >
        <span className="font-semibold text-zinc-900">
          {s.order}. {name}
        </span>{' '}
        <span className="text-sm text-zinc-600 tabular">
          {hhmm(s.start)}–{hhmm(s.end)} ({s.visitMinutes} min)
        </span>
        <div className="mt-0.5 flex flex-wrap items-center gap-1 text-xs text-zinc-600">
          <span>{s.cost.unknown ? 'cost not known' : `₹${s.cost.max}`}</span>
          <ConfidenceBadge confidence={s.cost.confidence as Confidence} />
          <span>· hours {label(s.hours.status.toLowerCase())}</span>
          <ConfidenceBadge confidence={s.hours.confidence as Confidence} />
          {s.kind === 'MUST_VISIT' && <span className="text-teal-800">· must-visit</span>}
        </div>
        <div className="text-xs text-zinc-500">{s.reason}</div>
        {actions && (
          <div className="mt-1 flex flex-wrap gap-1">
            <button
              type="button"
              aria-label={`Lock ${name}`}
              aria-pressed={locked}
              className={btn}
              onClick={() => actions.toggleLock(s)}
            >
              {locked ? 'Locked' : 'Lock'}
            </button>
            <button
              type="button"
              aria-label={`Remove ${name}`}
              disabled={actions.busy}
              className={btn}
              onClick={() => actions.edit({ kind: 'remove', placeId: s.place.id })}
            >
              Remove
            </button>
            <button
              type="button"
              aria-label={`Replace ${name}`}
              disabled={actions.busy}
              className={btn}
              onClick={() => actions.edit({ kind: 'replace', placeId: s.place.id })}
            >
              Replace
            </button>
            {days > 1 && (
              <select
                aria-label={`Move ${name} to day`}
                value={day}
                disabled={actions.busy}
                onChange={(e) =>
                  actions.edit({ kind: 'move', placeId: s.place.id, day: Number(e.target.value) })
                }
                className="rounded text-xs ring-1 ring-zinc-300"
              >
                {Array.from({ length: days }, (_, i) => (
                  <option key={i} value={i + 1}>
                    Day {i + 1}
                  </option>
                ))}
              </select>
            )}
          </div>
        )}
      </div>
    </li>
  )
}
