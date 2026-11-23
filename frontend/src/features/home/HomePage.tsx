import { Link } from 'react-router-dom'
import { PLAN_DEMOS, ROAD_DEMOS } from '../demos'

const MODES = [
  {
    to: '/plan?mode=today',
    title: 'Plan today',
    text: 'One day from where you are staying, starting now.',
  },
  {
    to: '/plan?mode=trip',
    title: 'Multi-day trip',
    text: 'A day-by-day Goa itinerary from your interests, pace and budget.',
  },
  {
    to: '/road-trip',
    title: 'Road trip',
    text: 'Fixed stops in your order, optional detours, driving days and overnight stays.',
  },
]

export function HomePage() {
  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-xl font-semibold">Where are you heading?</h1>
        <div className="mt-3 grid gap-3 sm:grid-cols-3">
          {MODES.map((m) => (
            <Link
              key={m.to}
              to={m.to}
              className="rounded border border-zinc-200 bg-white p-4 hover:border-teal-600"
            >
              <span className="block font-medium text-teal-800">{m.title}</span>
              <span className="text-sm text-zinc-600">{m.text}</span>
            </Link>
          ))}
        </div>
      </section>
      <section>
        <h2 className="font-medium">Demo scenarios</h2>
        <p className="text-sm text-zinc-500">
          Preloaded inputs on the bundled catalogue. They work without any provider keys; travel
          times fall back to labelled estimates when road routing is off.
        </p>
        <ul className="mt-2 grid gap-2 sm:grid-cols-2">
          {[
            ...PLAN_DEMOS.map((d) => ({ ...d, to: `/plan?demo=${d.id}` })),
            ...ROAD_DEMOS.map((d) => ({ ...d, to: `/road-trip?demo=${d.id}` })),
          ].map((d) => (
            <li key={d.id}>
              <Link
                to={d.to}
                className="block rounded border border-zinc-200 bg-white p-3 text-sm hover:border-teal-600"
              >
                <span className="block font-medium">{d.title}</span>
                <span className="text-zinc-600">{d.description}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
