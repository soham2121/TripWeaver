import type { Confidence } from '../types/place'

const STYLE: Record<Confidence, { label: string; cls: string; title: string }> = {
  VERIFIED: {
    label: 'Verified',
    cls: 'bg-emerald-100 text-emerald-800',
    title: 'Stated by an official source',
  },
  PROVIDER_SOURCED: {
    label: 'OSM',
    cls: 'bg-sky-100 text-sky-800',
    title: 'From OpenStreetMap data',
  },
  CURATED_ESTIMATE: {
    label: 'Estimate',
    cls: 'bg-amber-100 text-amber-800',
    title: 'TripWeaver estimate or non-official source',
  },
  USER_ENTERED: {
    label: 'You entered',
    cls: 'bg-violet-100 text-violet-800',
    title: 'Entered by you',
  },
  SYNTHETIC: { label: 'Synthetic', cls: 'bg-zinc-200 text-zinc-700', title: 'Generated test data' },
  UNKNOWN: { label: 'Unknown', cls: 'bg-zinc-100 text-zinc-600', title: 'Not known' },
}

export function ConfidenceBadge({ confidence }: { confidence: Confidence }) {
  const s = STYLE[confidence] ?? STYLE.UNKNOWN
  return (
    <span
      className={`inline-block rounded px-1.5 py-0.5 text-[11px] font-medium ${s.cls}`}
      title={s.title}
    >
      {s.label}
    </span>
  )
}
