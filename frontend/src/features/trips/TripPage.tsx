import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { tripsApi } from '../../api/trips'
import type { Itinerary, ItineraryRequest } from '../../types/itinerary'
import type { RoadTrip } from '../../types/roadtrip'
import type {
  Expenses,
  HistoryEntry,
  Role,
  ShareLink,
  Trip,
  TripSuggestion,
} from '../../types/trip'
import { Autocomplete } from '../discover/Autocomplete'
import { localTime } from '../../utils/format'
import { applyEdit, type Edit } from '../plan/edits'
import { ItineraryView } from '../plan/ItineraryView'
import { RoadTripView } from '../roadtrip/RoadTripPage'

const ROLE_TEXT: Record<Role, string> = {
  OWNER: 'You are the owner',
  EDITOR: 'You are an editor: you can edit the plan, suggest places, vote and add expenses',
  VIEWER: 'You are a viewer: read-only',
}

const POLL_MS = 15_000
const inr = (n: number) => '₹' + Math.abs(n).toLocaleString('en-IN', { maximumFractionDigits: 2 })
const when = localTime
const box = 'card p-3 text-sm'
const btn = 'btn-sm'

type Notice = { kind: 'error' | 'info'; text: string } | null

export function TripPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [trip, setTrip] = useState<Trip | null>(null)
  const [loadError, setLoadError] = useState('')
  const [notice, setNotice] = useState<Notice>(null)
  const [busy, setBusy] = useState(false)
  const [suggestions, setSuggestions] = useState<TripSuggestion[]>([])
  const [history, setHistory] = useState<HistoryEntry[]>([])
  const [expenses, setExpenses] = useState<Expenses | null>(null)
  const [links, setLinks] = useState<ShareLink[]>([])
  const [newLink, setNewLink] = useState<string | null>(null)

  const loadSide = useCallback(
    async (role: Role) => {
      const [s, h, e] = await Promise.all([
        tripsApi.suggestions(id),
        tripsApi.history(id),
        tripsApi.expenses(id),
      ])
      setSuggestions(s)
      setHistory(h)
      setExpenses(e)
      if (role === 'OWNER') setLinks(await tripsApi.shareLinks(id))
    },
    [id],
  )

  const load = useCallback(async () => {
    try {
      const t = await tripsApi.get(id)
      setTrip(t)
      await loadSide(t.role)
      return t
    } catch (e) {
      setLoadError(
        e instanceof ApiError && e.status === 404
          ? 'This trip does not exist or you do not have access to it.'
          : 'Could not load the trip.',
      )
      return null
    }
  }, [id, loadSide])

  useEffect(() => {
    void load()
  }, [load])

  // Polling keeps companions' views fresh (plan 8.2: polling is enough for the MVP).
  const live = useRef({ version: -1, busy: false })
  useEffect(() => {
    live.current = { version: trip?.version ?? -1, busy }
  })
  useEffect(() => {
    const timer = setInterval(() => {
      if (live.current.busy || document.hidden) return
      tripsApi
        .get(id)
        .then((t) => {
          if (t.version > live.current.version) {
            setTrip(t)
            setNotice({
              kind: 'info',
              text: 'A companion changed this trip; showing the latest version.',
            })
            void loadSide(t.role)
          }
        })
        .catch(() => undefined)
    }, POLL_MS)
    return () => clearInterval(timer)
  }, [id, loadSide])

  /** Runs a change; on a version conflict loads the latest trip instead of overwriting. */
  const act = async (f: () => Promise<unknown>, after?: (r: unknown) => void) => {
    setBusy(true)
    setNotice(null)
    try {
      const r = await f()
      after?.(r)
      if (trip) await loadSide(trip.role)
    } catch (e) {
      if (e instanceof ApiError && e.errorCode === 'VERSION_CONFLICT') {
        setNotice({ kind: 'error', text: e.message })
        await load()
      } else
        setNotice({
          kind: 'error',
          text: e instanceof ApiError ? e.message : 'Something went wrong.',
        })
    } finally {
      setBusy(false)
    }
  }

  if (loadError)
    return (
      <p role="alert" className={box}>
        {loadError}
      </p>
    )
  if (!trip) return <p role="status">Loading trip…</p>

  const canEdit = trip.role !== 'VIEWER'
  const owner = trip.role === 'OWNER'
  const saveTrip = (r: unknown) => setTrip(r as Trip)

  const onEdit = (e: Edit) =>
    void act(
      () =>
        tripsApi.generate(
          id,
          trip.version,
          applyEdit(trip.request as ItineraryRequest, trip.result as Itinerary, e),
        ),
      saveTrip,
    )

  return (
    <div className="space-y-3">
      <TitleBar
        trip={trip}
        busy={busy}
        act={act}
        onSaved={saveTrip}
        onDeleted={() => navigate('/trips')}
      />
      {notice && (
        <p
          role={notice.kind === 'error' ? 'alert' : 'status'}
          className={`rounded p-2 text-sm ${notice.kind === 'error' ? 'bg-amber-50 text-amber-900' : 'bg-teal-50 text-teal-900'}`}
        >
          {notice.text}
        </p>
      )}

      <div className="grid gap-3 xl:grid-cols-[1fr_340px]">
        <div className={busy ? 'opacity-60' : undefined}>
          {!trip.result && (
            <div className={box}>
              <p>No plan yet.</p>
              {canEdit && (
                <button
                  type="button"
                  className={btn}
                  disabled={busy}
                  onClick={() => void act(() => tripsApi.generate(id, trip.version), saveTrip)}
                >
                  Generate plan
                </button>
              )}
            </div>
          )}
          {trip.result && trip.kind === 'ITINERARY' && (
            <ItineraryView
              it={trip.result as Itinerary}
              base={{
                lat: (trip.request as ItineraryRequest).baseLat,
                lon: (trip.request as ItineraryRequest).baseLon,
              }}
              onEdit={canEdit ? onEdit : undefined}
              busy={busy}
            />
          )}
          {trip.result && trip.kind === 'ROAD_TRIP' && (
            <div className="space-y-2">
              {canEdit && (
                <button
                  type="button"
                  className={btn}
                  disabled={busy}
                  onClick={() => void act(() => tripsApi.generate(id, trip.version), saveTrip)}
                >
                  Re-plan with the saved stops
                </button>
              )}
              <RoadTripView trip={trip.result as RoadTrip} />
            </div>
          )}
        </div>

        <aside className="space-y-3">
          <People trip={trip} busy={busy} act={act} reload={load} />
          {owner && (
            <section aria-label="Share" className={box}>
              <h2 className="text-base font-semibold">Read-only link</h2>
              <p className="text-xs text-zinc-500">
                Anyone with the link can view the plan without signing in. Revoke it any time.
              </p>
              <ShareForm
                busy={busy}
                onCreate={(hours) =>
                  void act(
                    () => tripsApi.createShareLink(id, hours),
                    (r) => setNewLink(`${window.location.origin}/shared/${(r as ShareLink).token}`),
                  )
                }
              />
              {newLink && (
                <label className="mt-2 block text-xs">
                  Copy this link now; it is shown only once.
                  <input readOnly value={newLink} className="block w-full rounded border px-1" />
                </label>
              )}
              <ul className="mt-2 space-y-1 text-xs">
                {links.map((l) => (
                  <li key={l.id} className="flex items-center justify-between">
                    <span>
                      Created {when(l.createdAt)}
                      {l.expiresAt ? ` · expires ${when(l.expiresAt)}` : ' · no expiry'}
                      {!l.active && (l.revokedAt ? ' · revoked' : ' · expired')}
                    </span>
                    {l.active && (
                      <button
                        type="button"
                        className={btn}
                        disabled={busy}
                        onClick={() =>
                          void act(
                            () => tripsApi.revokeShareLink(id, l.id),
                            () => setNewLink(null),
                          )
                        }
                      >
                        Revoke
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          )}
          <Suggestions
            trip={trip}
            items={suggestions}
            busy={busy}
            act={act}
            onAddToPlan={
              canEdit && trip.kind === 'ITINERARY' && trip.result
                ? (placeId) => onEdit({ kind: 'must', placeId })
                : undefined
            }
          />
          {expenses && (
            <ExpensesPanel id={id} canEdit={canEdit} data={expenses} busy={busy} act={act} />
          )}
          <section aria-label="History" className={box}>
            <h2 className="text-base font-semibold">Changes</h2>
            <ol className="mt-1 space-y-0.5 text-xs text-zinc-600">
              {history.map((h, i) => (
                <li key={i}>
                  {when(h.at)} · {h.username ?? 'someone'}: {h.summary}
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </div>
    </div>
  )
}

type Act = (f: () => Promise<unknown>, after?: (r: unknown) => void) => Promise<void>

function TitleBar({
  trip,
  busy,
  act,
  onSaved,
  onDeleted,
}: {
  trip: Trip
  busy: boolean
  act: Act
  onSaved: (r: unknown) => void
  onDeleted: () => void
}) {
  const [title, setTitle] = useState(trip.title)
  const [confirm, setConfirm] = useState(false)
  const owner = trip.role === 'OWNER'
  return (
    <div className={box}>
      <h1 className="text-2xl font-bold">{trip.title}</h1>
      <p className="text-xs text-zinc-600">
        {ROLE_TEXT[trip.role]} · owner {trip.owner.username} · version {trip.version} · updated{' '}
        {when(trip.updatedAt)}
      </p>
      {owner && (
        <div className="mt-2 flex flex-wrap items-end gap-2">
          <label className="text-xs">
            Trip name
            <input
              value={title}
              maxLength={120}
              onChange={(e) => setTitle(e.target.value)}
              className="field w-auto"
            />
          </label>
          <button
            type="button"
            className={btn}
            disabled={busy || !title.trim() || title === trip.title}
            onClick={() =>
              void act(() => tripsApi.update(trip.id, { version: trip.version, title }), onSaved)
            }
          >
            Rename
          </button>
          {!confirm ? (
            <button type="button" className={btn} onClick={() => setConfirm(true)}>
              Delete trip
            </button>
          ) : (
            <button
              type="button"
              className={`${btn} text-red-700 ring-red-300`}
              disabled={busy}
              onClick={() => void act(() => tripsApi.remove(trip.id), onDeleted)}
            >
              Confirm delete (cannot be undone here)
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function People({
  trip,
  busy,
  act,
  reload,
}: {
  trip: Trip
  busy: boolean
  act: Act
  reload: () => Promise<unknown>
}) {
  const [username, setUsername] = useState('')
  const [role, setRole] = useState<'EDITOR' | 'VIEWER'>('VIEWER')
  const owner = trip.role === 'OWNER'
  return (
    <section aria-label="People" className={box}>
      <h2 className="text-base font-semibold">People</h2>
      <ul className="mt-1 space-y-1">
        <li>
          {trip.owner.username} <span className="text-xs text-zinc-500">owner</span>
        </li>
        {trip.members.map((m) => (
          <li key={m.userId} className="flex items-center gap-2">
            <span className="flex-1">{m.username}</span>
            {owner ? (
              <>
                <select
                  aria-label={`Role for ${m.username}`}
                  value={m.role}
                  disabled={busy}
                  onChange={(e) =>
                    void act(
                      () =>
                        tripsApi.changeRole(
                          trip.id,
                          m.userId,
                          e.target.value as 'EDITOR' | 'VIEWER',
                        ),
                      () => void reload(),
                    )
                  }
                  className="rounded text-xs ring-1 ring-zinc-300"
                >
                  <option value="EDITOR">editor</option>
                  <option value="VIEWER">viewer</option>
                </select>
                <button
                  type="button"
                  aria-label={`Remove ${m.username} from trip`}
                  className={btn}
                  disabled={busy}
                  onClick={() =>
                    void act(
                      () => tripsApi.removeMember(trip.id, m.userId),
                      () => void reload(),
                    )
                  }
                >
                  ✕
                </button>
              </>
            ) : (
              <span className="text-xs text-zinc-500">{m.role.toLowerCase()}</span>
            )}
          </li>
        ))}
      </ul>
      {owner && (
        <form
          className="mt-2 flex flex-wrap items-end gap-1"
          onSubmit={(e) => {
            e.preventDefault()
            void act(
              () => tripsApi.addMember(trip.id, username, role),
              () => {
                setUsername('')
                void reload()
              },
            )
          }}
        >
          <label className="flex-1 text-xs">
            Username
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="field"
            />
          </label>
          <select
            aria-label="New member role"
            value={role}
            onChange={(e) => setRole(e.target.value as 'EDITOR' | 'VIEWER')}
            className="rounded text-xs ring-1 ring-zinc-300"
          >
            <option value="VIEWER">viewer</option>
            <option value="EDITOR">editor</option>
          </select>
          <button type="submit" className={btn} disabled={busy || !username.trim()}>
            Add
          </button>
        </form>
      )}
    </section>
  )
}

function ShareForm({ busy, onCreate }: { busy: boolean; onCreate: (hours?: number) => void }) {
  const [hours, setHours] = useState('')
  return (
    <div className="mt-2 flex items-end gap-2">
      <label className="text-xs">
        Expires
        <select
          value={hours}
          onChange={(e) => setHours(e.target.value)}
          className="block rounded text-xs ring-1 ring-zinc-300"
        >
          <option value="">never</option>
          <option value="24">after 1 day</option>
          <option value="168">after 7 days</option>
        </select>
      </label>
      <button
        type="button"
        className={btn}
        disabled={busy}
        onClick={() => onCreate(hours ? Number(hours) : undefined)}
      >
        Create read-only link
      </button>
    </div>
  )
}

function Suggestions({
  trip,
  items,
  busy,
  act,
  onAddToPlan,
}: {
  trip: Trip
  items: TripSuggestion[]
  busy: boolean
  act: Act
  onAddToPlan?: (placeId: string) => void
}) {
  const [query, setQuery] = useState('')
  const [place, setPlace] = useState<{ id: string; name: string } | null>(null)
  const [note, setNote] = useState('')
  const canEdit = trip.role !== 'VIEWER'
  return (
    <section aria-label="Suggestions" className={box}>
      <h2 className="text-base font-semibold">Suggested places</h2>
      {items.length === 0 && <p className="text-xs text-zinc-500">No suggestions yet.</p>}
      <ul className="mt-1 space-y-2">
        {items.map((s) => (
          <li key={s.id}>
            <div className="flex items-center gap-1">
              <span className="flex-1 font-medium">{s.placeName}</span>
              <span className="text-xs text-zinc-600">
                ▲{s.up} ▼{s.down}
              </span>
              {canEdit && (
                <>
                  <button
                    type="button"
                    aria-label={`Vote for ${s.placeName}`}
                    aria-pressed={s.myVote === 1}
                    className={btn}
                    disabled={busy || s.myVote === 1}
                    onClick={() => void act(() => tripsApi.vote(trip.id, s.id, 1))}
                  >
                    ▲
                  </button>
                  <button
                    type="button"
                    aria-label={`Vote against ${s.placeName}`}
                    aria-pressed={s.myVote === -1}
                    className={btn}
                    disabled={busy || s.myVote === -1}
                    onClick={() => void act(() => tripsApi.vote(trip.id, s.id, -1))}
                  >
                    ▼
                  </button>
                </>
              )}
            </div>
            {s.note && <div className="text-xs text-zinc-600">{s.note}</div>}
            <div className="text-xs text-zinc-500">
              by {s.suggestedBy}
              {s.day ? ` · day ${s.day}` : ''}
              {onAddToPlan && (
                <button
                  type="button"
                  aria-label={`Add ${s.placeName} to the plan`}
                  className="ml-2 text-teal-700 underline"
                  disabled={busy}
                  onClick={() => onAddToPlan(s.placeId)}
                >
                  add to plan
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
      {canEdit && (
        <form
          className="mt-2 space-y-1"
          onSubmit={(e) => {
            e.preventDefault()
            if (!place) return
            void act(
              () => tripsApi.suggest(trip.id, place.id, note || undefined),
              () => {
                setPlace(null)
                setQuery('')
                setNote('')
              },
            )
          }}
        >
          <Autocomplete
            value={query}
            onChange={(v) => {
              setQuery(v)
              setPlace(null)
            }}
            onPick={(s) => {
              if (s.type === 'PLACE' && s.placeId) {
                setPlace({ id: s.placeId, name: s.label })
                setQuery(s.label)
              }
            }}
            destinations={trip.kind === 'ROAD_TRIP' ? ['konkan', 'goa'] : ['goa']}
            placesOnly
            label="Suggest a place"
            placeholder="Suggest a place"
          />
          <label className="block text-xs">
            Why (optional)
            <input
              value={note}
              maxLength={500}
              onChange={(e) => setNote(e.target.value)}
              className="field"
            />
          </label>
          <button type="submit" className={btn} disabled={busy || !place}>
            Suggest
          </button>
        </form>
      )}
    </section>
  )
}

function ExpensesPanel({
  id,
  canEdit,
  data,
  busy,
  act,
}: {
  id: string
  canEdit: boolean
  data: Expenses
  busy: boolean
  act: Act
}) {
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  return (
    <section aria-label="Expenses" className={box}>
      <h2 className="text-base font-semibold">Shared expenses</h2>
      <ul className="mt-1 text-xs text-zinc-700">
        {data.items.map((e) => (
          <li key={e.id}>
            {e.description}: {inr(e.amount)} paid by {e.paidBy}
          </li>
        ))}
      </ul>
      <p className="mt-1 text-xs">
        Total {inr(data.total)} · {inr(data.perPerson)} each, split equally
      </p>
      <table aria-label="Balances" className="mt-1 w-full text-xs">
        <tbody>
          {data.balances.map((b) => (
            <tr key={b.userId}>
              <td>{b.username}</td>
              <td className="text-right">
                {b.balance > 0.005
                  ? `is owed ${inr(b.balance)}`
                  : b.balance < -0.005
                    ? `owes ${inr(b.balance)}`
                    : 'settled'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {canEdit && (
        <form
          className="mt-2 flex flex-wrap items-end gap-1"
          onSubmit={(e) => {
            e.preventDefault()
            void act(
              () => tripsApi.addExpense(id, description, Number(amount)),
              () => {
                setDescription('')
                setAmount('')
              },
            )
          }}
        >
          <label className="flex-1 text-xs">
            What for
            <input
              value={description}
              maxLength={200}
              onChange={(e) => setDescription(e.target.value)}
              className="field"
            />
          </label>
          <label className="w-24 text-xs">
            Amount (₹)
            <input
              type="number"
              min={1}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="field"
            />
          </label>
          <button
            type="submit"
            className={btn}
            disabled={busy || !description.trim() || !(Number(amount) > 0)}
          >
            Add expense
          </button>
        </form>
      )}
    </section>
  )
}
