import type { Problem } from '../api/client'

export function ProblemNotice({ problem }: { problem: Problem }) {
  return (
    <div role="alert" className="rounded border border-amber-300 bg-amber-50 p-3 text-sm">
      <p className="font-medium">{problem.message}</p>
      <ul className="ml-4 list-disc">
        {problem.conflicts.map((c) => (
          <li key={c}>{c}</li>
        ))}
      </ul>
      {problem.extra && <p className="mt-1">{problem.extra}</p>}
      {problem.relaxations.length > 0 && (
        <>
          <p className="mt-2 font-medium">Try:</p>
          <ul className="ml-4 list-disc">
            {problem.relaxations.map((c) => (
              <li key={c}>{c}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

/** Shown when the backend fell back from road routing to straight-line estimates. */
export function ProviderNotice({ provider, basis }: { provider: string; basis: string }) {
  if (provider === 'OSRM')
    return (
      <p>
        <span className="font-medium">Road routing: OSRM</span>{' '}
        <span className="text-zinc-500">— {basis}</span>
      </p>
    )
  return (
    <p role="status" className="rounded bg-amber-50 px-2 py-1 text-amber-800">
      Road routing unavailable: travel times are straight-line estimates ({basis}).
    </p>
  )
}
