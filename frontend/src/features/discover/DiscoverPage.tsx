import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react'
import { placesApi, type SearchParams } from '../../api/places'
import { ConfidenceBadge } from '../../components/ConfidenceBadge'
import { INTERESTS, type Destination, type PlaceSummary, type Suggestion } from '../../types/place'
import { formatCost, formatDuration, label } from '../../utils/format'
import { PlaceDetailPanel } from '../place-details/PlaceDetailPanel'
import { Autocomplete } from './Autocomplete'

const PlaceMap = lazy(() => import('../../maps/PlaceMap'))
const PAGE = 30

export function DiscoverPage() {
  const [text, setText] = useState('')
  const [filters, setFilters] = useState<SearchParams>({ activitiesOnly: true })
  const [items, setItems] = useState<PlaceSummary[]>([])
  const [hasMore, setHasMore] = useState(false)
  const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('loading')
  const [selected, setSelected] = useState<string | null>(null)
  const [view, setView] = useState<'list' | 'map'>('list')
  const [dest, setDest] = useState<Destination | null>(null)

  useEffect(() => {
    placesApi
      .destinations()
      .then((ds) => setDest(ds.find((d) => d.slug === 'goa') ?? null))
      .catch(() => setDest(null))
  }, [])

  const load = useCallback((params: SearchParams, offset: number) => {
    setStatus('loading')
    placesApi
      .search({ ...params, limit: PAGE, offset })
      .then((p) => {
        setItems((prev) => (offset === 0 ? p.items : [...prev, ...p.items]))
        setHasMore(p.hasMore)
        setStatus('idle')
      })
      .catch(() => setStatus('error'))
  }, [])

  useEffect(() => load(filters, 0), [filters, load])

  const onPick = (s: Suggestion) => {
    if (s.type === 'PLACE' && s.placeId) {
      setSelected(s.placeId)
      return
    }
    setText('')
    if (s.type === 'INTEREST') setFilters((f) => ({ ...f, q: undefined, interest: s.value }))
    else if (s.type === 'REGION') setFilters((f) => ({ ...f, q: undefined, region: s.value }))
    else if (s.type === 'CATEGORY' || s.type === 'CUISINE')
      setFilters((f) => ({ ...f, q: undefined, category: s.value }))
    else setFilters((f) => ({ ...f, q: s.label }))
  }

  const points = useMemo(
    () => items.map((p) => ({ id: p.id, lat: p.lat, lon: p.lon, label: p.name })),
    [items],
  )

  return (
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_380px] [&>*]:min-w-0">
      <section className="min-w-0 space-y-3">
        <form
          onSubmit={(e) => {
            e.preventDefault()
            setFilters((f) => ({ ...f, q: text.trim() || undefined }))
          }}
          className="space-y-2"
        >
          <Autocomplete
            value={text}
            onChange={setText}
            onPick={onPick}
            placeholder="Search Goa: a place, area, cuisine or interest"
          />
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <select
              aria-label="Interest"
              className="field w-auto max-w-full min-w-0"
              value={filters.interest ?? ''}
              onChange={(e) => setFilters((f) => ({ ...f, interest: e.target.value || undefined }))}
            >
              <option value="">All interests</option>
              {INTERESTS.map((i) => (
                <option key={i} value={i}>
                  {label(i)}
                </option>
              ))}
            </select>
            <select
              aria-label="Region"
              className="field w-auto max-w-full min-w-0"
              value={filters.region ?? ''}
              onChange={(e) => setFilters((f) => ({ ...f, region: e.target.value || undefined }))}
            >
              <option value="">All of Goa</option>
              {dest?.regions.map((r) => (
                <option key={r.slug} value={r.slug}>
                  {r.name}
                </option>
              ))}
            </select>
            <label className="flex items-center gap-1">
              <input
                type="checkbox"
                checked={!!filters.activitiesOnly}
                onChange={(e) => setFilters((f) => ({ ...f, activitiesOnly: e.target.checked }))}
              />
              Activities only
            </label>
            {(filters.q || filters.category) && (
              <button
                type="button"
                className="rounded bg-zinc-100 px-2 py-1"
                onClick={() => setFilters((f) => ({ ...f, q: undefined, category: undefined }))}
              >
                Clear “{filters.q ?? label(filters.category ?? '')}” ✕
              </button>
            )}
            <div className="ml-auto flex overflow-hidden rounded-full bg-white p-0.5 ring-1 ring-zinc-300">
              {(['list', 'map'] as const).map((v) => (
                <button
                  type="button"
                  key={v}
                  aria-pressed={view === v}
                  onClick={() => setView(v)}
                  className={`rounded-full px-3 py-1 text-sm font-medium ${view === v ? 'bg-teal-700 text-white' : 'text-zinc-600 hover:text-zinc-900'}`}
                >
                  {v === 'list' ? 'List' : 'Map'}
                </button>
              ))}
            </div>
          </div>
        </form>

        {status === 'error' && (
          <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
            Could not load places. Is the API running?
          </p>
        )}

        {view === 'map' && (
          <Suspense fallback={<div className="h-[480px] rounded bg-zinc-100" />}>
            <PlaceMap
              points={points}
              selectedId={selected}
              onSelect={setSelected}
              className="h-[480px]"
            />
          </Suspense>
        )}

        {status !== 'loading' && items.length === 0 && status !== 'error' && (
          <p className="text-sm text-zinc-600">No places match. Try a different filter.</p>
        )}

        <ul className="divide-y divide-zinc-100 card">
          {items.map((p) => (
            <li key={p.id}>
              <button
                className={`w-full px-3 py-2 text-left hover:bg-zinc-50 ${selected === p.id ? 'bg-teal-50' : ''}`}
                onClick={() => setSelected(p.id)}
              >
                <div className="flex items-baseline justify-between gap-2">
                  <span className="font-medium">{p.name}</span>
                  <span className="shrink-0 text-xs text-zinc-500">{label(p.primaryCategory)}</span>
                </div>
                <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-zinc-600">
                  <span>{p.neighbourhood ?? p.regionName}</span>
                  {p.activity && formatDuration(p.visitMinutesMin, p.visitMinutesMax) && (
                    <span>⏱ {formatDuration(p.visitMinutesMin, p.visitMinutesMax)}</span>
                  )}
                  {p.costs.length > 0 ? (
                    <span>
                      {formatCost(p.costs[0])}{' '}
                      <ConfidenceBadge confidence={p.costs[0].confidence} />
                    </span>
                  ) : (
                    p.activity && <span className="text-amber-700">Cost unknown</span>
                  )}
                </div>
              </button>
            </li>
          ))}
        </ul>
        {status === 'loading' && <p className="text-sm text-zinc-500">Loading…</p>}
        {hasMore && status === 'idle' && (
          <button
            className="btn-secondary py-1 text-sm"
            onClick={() => load(filters, items.length)}
          >
            Load more
          </button>
        )}
      </section>

      <aside className="card lg:sticky lg:top-4 lg:max-h-[calc(100vh-2rem)] lg:overflow-auto">
        {selected ? (
          <PlaceDetailPanel placeId={selected} onClose={() => setSelected(null)} />
        ) : (
          <p className="p-4 text-sm text-zinc-500">
            Select a place to see details, sources and data confidence.
            {dest && (
              <span className="mt-2 block">
                Catalogue: {dest.activityCount.toLocaleString()} activities and{' '}
                {(dest.placeCount - dest.activityCount).toLocaleString()} stays across{' '}
                {dest.regions.length} regions.
              </span>
            )}
          </p>
        )}
      </aside>
    </div>
  )
}
