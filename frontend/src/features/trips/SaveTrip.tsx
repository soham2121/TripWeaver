import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { tripsApi } from '../../api/trips'
import type { TripKind } from '../../types/trip'
import { useAuth } from '../auth/auth'
import { LoginPage } from '../auth/LoginPage'

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
  const navigate = useNavigate()
  const [title, setTitle] = useState(defaultTitle)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [signingIn, setSigningIn] = useState(false)

  // Sign in on this page: going to /login would unmount the planner and lose the plan on screen.
  if (!user)
    return signingIn ? (
      <LoginPage inline />
    ) : (
      <p className="card p-2 text-sm">
        <button
          type="button"
          onClick={() => setSigningIn(true)}
          className="text-teal-700 underline"
        >
          Sign in to save this trip
        </button>{' '}
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
      className="flex flex-wrap items-end gap-2 card p-2 text-sm"
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
          className="field"
        />
      </label>
      <button type="submit" disabled={busy} className="btn-primary">
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
