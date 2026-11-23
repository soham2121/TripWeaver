import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { itinerariesApi } from '../../api/itineraries'
import { placesApi } from '../../api/places'
import { toProblem, type Problem } from '../../api/client'
import { ProblemNotice } from '../../components/ProblemNotice'
import type { Itinerary, ItineraryRequest, Pace, Transport } from '../../types/itinerary'
import { INTERESTS, type Suggestion } from '../../types/place'
import { label } from '../../utils/format'
import { Autocomplete } from '../discover/Autocomplete'
import { CANDOLIM, DEFAULT_PLAN, PLAN_DEMOS, type PlanForm } from '../demos'
import { SaveTrip } from '../trips/SaveTrip'
import { applyEdit, type Edit } from './edits'
import { ItineraryView } from './ItineraryView'

const STEPS = ['Interests', 'Where and when', 'Travel and budget']

const isoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** Today mode: one day starting at the next quarter hour (not before 09:00); after 17:00, tomorrow. */
function todayForm(now = new Date()): { form: PlanForm; note: string } {
  const mins = Math.ceil((now.getHours() * 60 + now.getMinutes()) / 15) * 15
  if (mins > 17 * 60) {
    const t = new Date(now)
    t.setDate(t.getDate() + 1)
    return {
      form: { ...DEFAULT_PLAN, days: 1, startDate: isoDate(t) },
      note: 'It is late in the day, so this plans tomorrow.',
    }
  }
  const start = Math.max(mins, 9 * 60)
  const hm = `${String(Math.floor(start / 60)).padStart(2, '0')}:${String(start % 60).padStart(2, '0')}`
  return {
    form: { ...DEFAULT_PLAN, days: 1, startDate: isoDate(now), dayStart: hm, dayEnd: '21:00' },
    note: `Starts at ${hm} today.`,
  }
}

/** No date chosen: today, or tomorrow once the day is mostly over (after 17:00). */
function defaultStart(now = new Date()): string {
  const d = new Date(now)
  if (d.getHours() >= 17) d.setDate(d.getDate() + 1)
  return isoDate(d)
}

function toRequest(f: PlanForm): ItineraryRequest {
  const budget = Number(f.budgetAmount)
  const food = Number(f.foodPerPersonPerDay)
  return {
    baseLat: f.base.lat,
    baseLon: f.base.lon,
    baseName: f.base.name,
    startDate: f.startDate || defaultStart(),
    days: Math.max(1, f.days),
    partySize: f.partySize,
    interests: f.weights,
    pace: f.pace,
    transport: f.transport,
    dayStart: f.dayStart,
    dayEnd: f.dayEnd,
    ...(budget > 0 && {
      budget: {
        amount: budget,
        basis: f.budgetBasis,
        strictness: f.hardBudget ? ('HARD' as const) : ('FLEXIBLE' as const),
      },
    }),
    ...(food > 0 && { costs: { foodPerPersonPerDay: food } }),
  }
}

export function PlanPage() {
  const [params] = useSearchParams()
  const today = params.get('mode') === 'today'
  const demo = PLAN_DEMOS.find((d) => d.id === params.get('demo'))
  const [initial] = useState(() =>
    demo
      ? { form: { ...DEFAULT_PLAN, ...demo.form }, note: `Demo: ${demo.title}` }
      : today
        ? todayForm()
        : { form: DEFAULT_PLAN, note: '' },
  )
  const [form, setForm] = useState<PlanForm>(initial.form)
  const [step, setStep] = useState(0)
  const [baseQuery, setBaseQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<Itinerary | null>(null)
  /** The request behind the plan on screen; it carries earlier edits (avoid, must-visit). */
  const [lastRequest, setLastRequest] = useState<ItineraryRequest | null>(null)
  const [problem, setProblem] = useState<Problem | null>(null)

  const set = <K extends keyof PlanForm>(k: K, v: PlanForm[K]) => setForm((f) => ({ ...f, [k]: v }))

  const run = async (req: ItineraryRequest) => {
    setLoading(true)
    setProblem(null)
    try {
      const r = await itinerariesApi.generate(req)
      setResult(r.itinerary)
      setLastRequest(req)
    } catch (e) {
      setProblem(toProblem(e, 'Could not reach the planner.'))
    } finally {
      setLoading(false)
    }
  }

  /** A fresh plan from the wizard clears earlier edits. */
  const generate = (f = form) => {
    setResult(null)
    void run(toRequest(f))
  }

  const started = useRef(false)
  useEffect(() => {
    if (demo && !started.current) {
      started.current = true
      generate(initial.form)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onEdit = (e: Edit) => {
    if (result && lastRequest) void run(applyEdit(lastRequest, result, e))
  }

  const pickBase = async (s: Suggestion) => {
    if (s.type !== 'PLACE' || !s.placeId) return
    try {
      const d = await placesApi.get(s.placeId)
      set('base', { name: d.summary.name, lat: d.summary.lat, lon: d.summary.lon })
      setBaseQuery(d.summary.name)
    } catch {
      /* keep previous base */
    }
  }

  const toggle = (i: string) =>
    setForm((f) => {
      const w = { ...f.weights }
      if (i in w) delete w[i]
      else w[i] = 3
      return { ...f, weights: w }
    })

  const input = 'block w-full rounded border border-zinc-300 px-1'

  return (
    <div className="grid gap-4 lg:grid-cols-[320px_1fr]">
      <form
        className="space-y-3 self-start rounded border border-zinc-200 bg-white p-3 text-sm"
        onSubmit={(e) => {
          e.preventDefault()
          generate()
        }}
      >
        <h1 className="text-base font-semibold">
          {today ? 'Plan today' : 'Plan a multi-day trip'}
        </h1>
        {initial.note && <p className="text-xs text-teal-800">{initial.note}</p>}
        <ol className="flex gap-1 text-xs">
          {STEPS.map((s, i) => (
            <li key={s}>
              <button
                type="button"
                aria-current={i === step ? 'step' : undefined}
                onClick={() => setStep(i)}
                className={`rounded px-2 py-1 ${i === step ? 'bg-teal-700 text-white' : 'bg-zinc-100'}`}
              >
                {i + 1}. {s}
              </button>
            </li>
          ))}
        </ol>

        {step === 0 && (
          <fieldset>
            <legend className="font-medium">What do you enjoy?</legend>
            <p className="text-xs text-zinc-500">
              Tick interests, then set how much each matters (1–5).
            </p>
            <div className="mt-1 grid grid-cols-2 gap-1">
              {INTERESTS.map((i) => (
                <div key={i} className="flex items-center gap-1">
                  <label className="flex flex-1 items-center gap-1">
                    <input type="checkbox" checked={i in form.weights} onChange={() => toggle(i)} />
                    {label(i)}
                  </label>
                  {i in form.weights && (
                    <select
                      aria-label={`${label(i)} importance`}
                      value={form.weights[i]}
                      onChange={(e) =>
                        set('weights', { ...form.weights, [i]: Number(e.target.value) })
                      }
                      className="rounded border border-zinc-300 text-xs"
                    >
                      {[1, 2, 3, 4, 5].map((n) => (
                        <option key={n}>{n}</option>
                      ))}
                    </select>
                  )}
                </div>
              ))}
            </div>
          </fieldset>
        )}

        {step === 1 && (
          <div className="space-y-2">
            <div>
              <span className="font-medium">Stay near</span>
              <Autocomplete
                value={baseQuery}
                onChange={setBaseQuery}
                onPick={(s) => void pickBase(s)}
                placesOnly
                label="Stay near"
                placeholder="Search a place for your base"
              />
              <p className="text-xs text-zinc-500">
                Base: {form.base.name}
                {form.base === CANDOLIM && ' (default)'}
              </p>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <label>
                Start date
                <input
                  type="date"
                  value={form.startDate}
                  onChange={(e) => set('startDate', e.target.value)}
                  className={input}
                />
              </label>
              {!today && (
                <label>
                  Days
                  <input
                    type="number"
                    min={1}
                    max={14}
                    value={form.days || ''}
                    onChange={(e) => set('days', Math.min(14, Number(e.target.value) || 0))}
                    className={input}
                  />
                </label>
              )}
              <label>
                Day starts
                <input
                  type="time"
                  value={form.dayStart}
                  onChange={(e) => set('dayStart', e.target.value)}
                  className={input}
                />
              </label>
              <label>
                Day ends
                <input
                  type="time"
                  value={form.dayEnd}
                  onChange={(e) => set('dayEnd', e.target.value)}
                  className={input}
                />
              </label>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="grid grid-cols-2 gap-2">
            <label>
              Pace
              <select
                value={form.pace}
                onChange={(e) => set('pace', e.target.value as Pace)}
                className={input}
              >
                <option value="RELAXED">Relaxed</option>
                <option value="BALANCED">Balanced</option>
                <option value="PACKED">Packed</option>
              </select>
            </label>
            <label>
              Transport
              <select
                value={form.transport}
                onChange={(e) => set('transport', e.target.value as Transport)}
                className={input}
              >
                <option value="CAR">Car</option>
                <option value="SCOOTER">Scooter</option>
                <option value="TAXI">Taxi</option>
                <option value="WALK">Walk</option>
              </select>
            </label>
            <label>
              People
              <input
                type="number"
                min={1}
                max={20}
                value={form.partySize}
                onChange={(e) => set('partySize', Math.max(1, Number(e.target.value) || 1))}
                className={input}
              />
            </label>
            <label>
              Budget (₹)
              <input
                type="number"
                min={0}
                value={form.budgetAmount}
                onChange={(e) => set('budgetAmount', e.target.value)}
                className={input}
              />
            </label>
            <label>
              Per person or total
              <select
                value={form.budgetBasis}
                onChange={(e) => set('budgetBasis', e.target.value as PlanForm['budgetBasis'])}
                className={input}
              >
                <option value="PER_PERSON">Per person</option>
                <option value="TOTAL">Total</option>
              </select>
            </label>
            <label className="flex items-center gap-1 self-end">
              <input
                type="checkbox"
                checked={form.hardBudget}
                onChange={(e) => set('hardBudget', e.target.checked)}
              />
              Hard limit
            </label>
            <label className="col-span-2">
              Food per person per day (₹)
              <input
                type="number"
                min={0}
                value={form.foodPerPersonPerDay}
                onChange={(e) => set('foodPerPersonPerDay', e.target.value)}
                className={input}
              />
            </label>
            <p className="col-span-2 text-xs text-zinc-500">
              A hard limit leaves out stops that would exceed it; a flexible one only warns. Costs
              you leave blank stay unknown and are never counted as ₹0.
            </p>
          </div>
        )}

        <div className="flex gap-2">
          {step > 0 && (
            <button
              type="button"
              onClick={() => setStep(step - 1)}
              className="rounded px-3 py-2 ring-1 ring-zinc-300"
            >
              Back
            </button>
          )}
          {step < STEPS.length - 1 && (
            <button
              type="button"
              onClick={() => setStep(step + 1)}
              className="rounded px-3 py-2 ring-1 ring-zinc-300"
            >
              Next
            </button>
          )}
        </div>
        <button
          type="submit"
          disabled={loading || Object.keys(form.weights).length === 0}
          className="w-full rounded bg-teal-700 px-3 py-2 font-medium text-white disabled:opacity-50"
        >
          {loading ? 'Planning…' : 'Generate plan'}
        </button>
      </form>

      <section aria-live="polite" aria-busy={loading}>
        {loading && (
          <p role="status" className="mb-2 text-sm text-zinc-600">
            Planning your days…
          </p>
        )}
        {problem && <ProblemNotice problem={problem} />}
        {!result && !problem && !loading && (
          <p className="text-sm text-zinc-500">
            Choose interests and generate a day-by-day plan. You can then lock, remove, replace or
            move stops and replan.
          </p>
        )}
        {result && lastRequest && (
          <div className={`space-y-3 ${loading ? 'opacity-60' : ''}`}>
            <SaveTrip
              kind="ITINERARY"
              request={lastRequest}
              result={result}
              defaultTitle={`${result.days.length}-day trip from ${lastRequest.baseName ?? 'Goa'}`}
            />
            <ItineraryView
              it={result}
              base={{ lat: lastRequest.baseLat, lon: lastRequest.baseLon }}
              onEdit={onEdit}
              busy={loading}
            />
          </div>
        )}
      </section>
    </div>
  )
}
