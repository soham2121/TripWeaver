import { useEffect, useState } from 'react'
import { placesApi } from '../../api/places'
import { ConfidenceBadge } from '../../components/ConfidenceBadge'
import type { PlaceDetail } from '../../types/place'
import { formatCost, formatDuration, label } from '../../utils/format'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

export function PlaceDetailPanel({ placeId, onClose }: { placeId: string; onClose?: () => void }) {
  const [state, setState] = useState<
    { status: 'loading' } | { status: 'error' } | { status: 'ok'; d: PlaceDetail }
  >({ status: 'loading' })

  useEffect(() => {
    let live = true
    setState({ status: 'loading' })
    placesApi
      .get(placeId)
      .then((d) => live && setState({ status: 'ok', d }))
      .catch(() => live && setState({ status: 'error' }))
    return () => {
      live = false
    }
  }, [placeId])

  if (state.status === 'loading') return <p className="p-4 text-sm text-zinc-500">Loading place…</p>
  if (state.status === 'error')
    return <p className="p-4 text-sm text-red-700">Could not load this place.</p>
  const { d } = state
  const s = d.summary
  const duration = formatDuration(s.visitMinutesMin, s.visitMinutesMax)

  return (
    <article className="space-y-4 p-4 text-sm" aria-label={`Details for ${s.name}`}>
      <header className="flex items-start justify-between gap-2">
        <div>
          <h2 className="text-lg font-semibold">{s.name}</h2>
          <p className="text-zinc-600">
            {label(s.primaryCategory)}
            {s.neighbourhood ? ` · ${s.neighbourhood}` : ''}
            {s.regionName ? ` · ${s.regionName}` : ''}
          </p>
        </div>
        {onClose && (
          <button
            onClick={onClose}
            className="rounded px-2 py-1 text-zinc-500 hover:bg-zinc-100"
            aria-label="Close details"
          >
            ✕
          </button>
        )}
      </header>

      {d.description && <p>{d.description}</p>}

      <section>
        <h3 className="font-medium">Interests</h3>
        <div className="mt-1 flex flex-wrap gap-1">
          {s.interests.map((i) => (
            <span key={i} className="rounded bg-teal-50 px-2 py-0.5 text-teal-800">
              {label(i)}
            </span>
          ))}
        </div>
      </section>

      {s.activity && (
        <section>
          <h3 className="font-medium">Visit duration</h3>
          <p>
            {duration ?? 'Unknown'} <ConfidenceBadge confidence={d.visitMinutesConfidence} />
          </p>
          {d.visitMinutesBasis && <p className="text-xs text-zinc-500">{d.visitMinutesBasis}</p>}
        </section>
      )}

      <section>
        <h3 className="font-medium">Cost</h3>
        {d.costs.length === 0 ? (
          <p className="text-amber-800">Cost unknown. Not included in budgets as zero.</p>
        ) : (
          <ul className="space-y-1">
            {d.costs.map((c, i) => (
              <li key={i}>
                <span className="text-zinc-500">{label(c.kind.toLowerCase())}:</span>{' '}
                {formatCost(c)} <ConfidenceBadge confidence={c.confidence} />
                {c.note && <span className="block text-xs text-zinc-500">{c.note}</span>}
                {c.sourceUrl && (
                  <a
                    className="block text-xs text-teal-700 underline"
                    href={c.sourceUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Source
                  </a>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h3 className="font-medium">Opening hours</h3>
        {d.openingHours.length === 0 ? (
          <p className="text-zinc-600">Unknown</p>
        ) : (
          d.openingHours.map((h, i) => (
            <p key={i}>
              <code className="rounded bg-zinc-100 px-1">{h.raw}</code>{' '}
              <ConfidenceBadge confidence={h.confidence} />
              {h.verifiedAt && (
                <span className="ml-1 text-xs text-zinc-500">
                  as of {h.verifiedAt.slice(0, 10)}
                </span>
              )}
            </p>
          ))
        )}
      </section>

      {d.seasonal && (
        <section className="rounded border border-amber-200 bg-amber-50 p-2">
          <h3 className="font-medium">Seasonal note</h3>
          <p>{d.seasonal.notes}</p>
          {d.seasonal.closedMonths.length > 0 && (
            <p className="text-xs">
              Treated as closed: {d.seasonal.closedMonths.map((m) => MONTHS[m - 1]).join(', ')}
            </p>
          )}
        </section>
      )}

      {s.missingFields.length > 0 && (
        <p className="text-xs text-zinc-500">Missing data: {s.missingFields.join(', ')}</p>
      )}

      <details>
        <summary className="cursor-pointer font-medium">Sources ({d.sources.length})</summary>
        <ul className="mt-1 space-y-1 text-xs">
          {d.sources.map((src, i) => (
            <li key={i}>
              <span className="font-medium">{src.field}</span> — {src.sourceType}{' '}
              <ConfidenceBadge confidence={src.confidence} />
              {src.sourceUrl && (
                <a
                  className="ml-1 text-teal-700 underline"
                  href={src.sourceUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  link
                </a>
              )}
              {src.note && <span className="block text-zinc-500">{src.note}</span>}
            </li>
          ))}
        </ul>
      </details>

      <footer className="border-t pt-2 text-xs text-zinc-500">
        {d.osmUrl && (
          <a className="underline" href={d.osmUrl} target="_blank" rel="noreferrer">
            View on OpenStreetMap
          </a>
        )}{' '}
        · {d.attribution}
      </footer>
    </article>
  )
}
