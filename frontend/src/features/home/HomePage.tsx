import { Link } from 'react-router-dom'
import { PLAN_DEMOS, ROAD_DEMOS } from '../demos'

type IconName = 'today' | 'trip' | 'road'

/** Line icons, one stroke weight, drawn for these three modes. */
function ModeIcon({ name }: { name: IconName }) {
  const common = {
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
  }
  return (
    <svg viewBox="0 0 24 24" className="size-6" aria-hidden>
      {name === 'today' && (
        <>
          <circle cx="12" cy="12" r="8.5" {...common} />
          <path d="M12 7.5V12l3 2" {...common} />
        </>
      )}
      {name === 'trip' && (
        <>
          <rect x="3.5" y="5" width="17" height="15" rx="2.5" {...common} />
          <path d="M3.5 10h17M8 3v4M16 3v4M8 14h3M8 17h6" {...common} />
        </>
      )}
      {name === 'road' && (
        <>
          <circle cx="6" cy="18" r="2" {...common} />
          <circle cx="18" cy="6" r="2" {...common} />
          <path d="M8 18h7.5a3.5 3.5 0 0 0 0-7h-7a3.5 3.5 0 0 1 0-7H16" {...common} />
        </>
      )}
    </svg>
  )
}

const MODES: { to: string; title: string; text: string; icon: IconName }[] = [
  {
    to: '/plan?mode=today',
    title: 'Plan today',
    text: 'One day from where you are staying, starting now.',
    icon: 'today',
  },
  {
    to: '/plan?mode=trip',
    title: 'Multi-day trip',
    text: 'A day-by-day Goa itinerary from your interests, pace and budget.',
    icon: 'trip',
  },
  {
    to: '/road-trip',
    title: 'Road trip',
    text: 'Fixed stops in your order, optional detours, driving days and overnight stays.',
    icon: 'road',
  },
]

export function HomePage() {
  const demos = [
    ...PLAN_DEMOS.map((d) => ({ ...d, to: `/plan?demo=${d.id}`, kind: 'Itinerary' })),
    ...ROAD_DEMOS.map((d) => ({ ...d, to: `/road-trip?demo=${d.id}`, kind: 'Road trip' })),
  ]
  return (
    <div className="space-y-12 pb-4">
      <section className="grid items-start gap-8 pt-4 md:grid-cols-[1fr_1.15fr] md:gap-12">
        <div className="md:pt-6">
          <h1 className="font-display text-4xl font-bold leading-[1.05] tracking-tight text-zinc-900 sm:text-5xl">
            Where are you heading?
          </h1>
          <p className="mt-4 max-w-sm text-zinc-600">
            Pick how you want to travel. TripWeaver chooses the places, splits them across days and
            orders each day&apos;s route.
          </p>
        </div>
        <ul className="card divide-y divide-zinc-100 overflow-hidden p-1.5">
          {MODES.map((m) => (
            <li key={m.to}>
              <Link
                to={m.to}
                viewTransition
                className="group flex items-center gap-4 rounded-md px-4 py-4 hover:bg-teal-50"
              >
                <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-teal-50 text-teal-700 transition-colors group-hover:bg-teal-700 group-hover:text-white">
                  <ModeIcon name={m.icon} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block font-semibold text-zinc-900">{m.title}</span>
                  <span className="block text-sm text-zinc-600">{m.text}</span>
                </span>
                <svg
                  viewBox="0 0 20 20"
                  className="size-5 shrink-0 text-zinc-400 transition-transform duration-200 ease-out group-hover:translate-x-1 group-hover:text-teal-700"
                  aria-hidden
                >
                  <path
                    d="M7.5 4.5 13 10l-5.5 5.5"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section>
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-xl font-semibold">Demo scenarios</h2>
          <p className="text-sm text-zinc-500">
            Preloaded inputs on the bundled catalogue. They work without any provider keys; travel
            times fall back to labelled estimates when road routing is off.
          </p>
        </div>
        <ul className="mt-4 grid gap-3 sm:grid-cols-2">
          {demos.map((d) => (
            <li key={d.id}>
              <Link to={d.to} viewTransition className="card-link h-full p-4">
                <span className="flex items-center justify-between gap-3">
                  <span className="font-semibold text-zinc-900">{d.title}</span>
                  <span
                    aria-hidden
                    className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold ${d.kind === 'Road trip' ? 'bg-clay-50 text-clay-700' : 'bg-teal-50 text-teal-700'}`}
                  >
                    {d.kind}
                  </span>
                </span>
                <span className="mt-1 block text-sm text-zinc-600">{d.description}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
