import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { tripsApi } from '../../api/trips'
import type { TripKind } from '../../types/trip'
import { useAuth } from '../auth/auth'

/** Saves the plan on screen as a trip (signed-in users) so it can be edited later and shared. */
export function SaveTrip({
  kind,
  request,
  result,
  defaultTitle,
}: {
  kind: TripKind
  request: unknown
  result: unknown
  defaultTitle: string
}) {
  const { user } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [title, setTitle] = useState(defaultTitle)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (!user)
    return (
      <p className="rounded border border-zinc-200 bg-white p-2 text-sm">
        <Link
          to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}
          className="text-teal-700 underline"
        >
          Sign in to save this trip
        </Link>{' '}
        and share it with your travel companions.
      </p>
    )

  const save = async () => {
    setBusy(true)
    setError('')
    try {
      const t = await tripsApi.create(title, kind, request, result)
      navigate(`/trips/${t.id}`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save the trip.')
      setBusy(false)
    }
  }

  return (
    <form
      className="flex flex-wrap items-end gap-2 rounded border border-zinc-200 bg-white p-2 text-sm"
      onSubmit={(e) => {
        e.preventDefault()
        void save()
      }}
    >
      <label className="flex-1">
        Trip name
        <input
          value={title}
          maxLength={120}
          onChange={(e) => setTitle(e.target.value)}
          className="block w-full rounded border border-zinc-300 px-1"
        />
      </label>
      <button
        type="submit"
        disabled={busy}
        className="rounded bg-teal-700 px-3 py-1.5 text-white disabled:opacity-50"
      >
        Save trip
      </button>
      {error && (
        <p role="alert" className="w-full text-amber-800">
          {error}
        </p>
      )}
    </form>
  )
}
