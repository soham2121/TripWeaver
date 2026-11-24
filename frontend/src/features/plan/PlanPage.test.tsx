import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, test, vi } from 'vitest'
import { mockApi } from '../../test/mockApi'
import { itineraryResponse } from '../../test/planFixtures'
import { AuthContext } from '../auth/auth'
import { PlanPage } from './PlanPage'

const mapProps: { points: unknown[]; line?: unknown[] }[] = []
vi.mock('../../maps/PlaceMap', () => ({
  default: (p: { points: unknown[]; line?: unknown[] }) => {
    mapProps.push(p)
    return <div data-testid="map" />
  },
}))
afterEach(() => {
  vi.unstubAllGlobals()
  mapProps.length = 0
})

const renderAt = (url = '/plan') =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <PlanPage />
    </MemoryRouter>,
  )

const bodyOf = (fetch: ReturnType<typeof mockApi>, call: number) =>
  JSON.parse(String(fetch.mock.calls[call][1]?.body))

const generateCalls = (fetch: ReturnType<typeof mockApi>) =>
  fetch.mock.calls.filter((c) => String(c[0]).includes('/itineraries/generate'))

async function planBeach() {
  const fetch = mockApi([[/\/itineraries\/generate$/, () => itineraryResponse()]])
  renderAt()
  await userEvent.click(screen.getByRole('checkbox', { name: /beach/i }))
  await userEvent.click(screen.getByRole('button', { name: /generate plan/i }))
  await screen.findByRole('list', { name: /day 1 stops/i })
  return fetch
}

test('wizard walks through interests, where and when, travel and budget', async () => {
  const fetch = mockApi([[/\/itineraries\/generate$/, () => itineraryResponse()]])
  renderAt()
  await userEvent.click(screen.getByRole('checkbox', { name: /beach/i }))
  await userEvent.click(screen.getByRole('button', { name: /next/i }))
  const days = screen.getByLabelText(/^days$/i)
  await userEvent.clear(days)
  await userEvent.type(days, '3')
  await userEvent.click(screen.getByRole('button', { name: /next/i }))
  await userEvent.type(screen.getByLabelText(/budget/i), '5000')
  await userEvent.click(screen.getByRole('checkbox', { name: /hard limit/i }))
  await userEvent.type(screen.getByLabelText(/food per person per day/i), '800')
  await userEvent.click(screen.getByRole('button', { name: /generate plan/i }))
  await screen.findByRole('list', { name: /day 1 stops/i })

  const body = bodyOf(fetch, 0)
  expect(body.interests).toEqual({ beach: 3 })
  expect(body.days).toBe(3)
  expect(body.budget).toEqual({ amount: 5000, basis: 'PER_PERSON', strictness: 'HARD' })
  expect(body.costs).toEqual({ foodPerPersonPerDay: 800 })
  expect(body.baseLat).toBeCloseTo(15.5202)
})

test('shows timeline, route, ordering and navigation for a day', async () => {
  await planBeach()
  const list = screen.getByRole('list', { name: /day 1 stops/i })
  expect(within(list).getByText(/1\. Candolim Beach/)).toBeInTheDocument()
  expect(within(list).getByText(/2\. Fort Aguada/)).toBeInTheDocument()
  expect(within(list).getByText(/Lunch 12:30/)).toBeInTheDocument()
  expect(screen.getByText(/Road routing: OSRM/)).toBeInTheDocument()
  expect(screen.getByText(/≤ 2 × optimal/)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /open in google maps/i })).toHaveAttribute(
    'href',
    itineraryResponse().itinerary.days[0].navigationUrls[0],
  )
  await screen.findByTestId('map')
  const last = mapProps.at(-1)!
  expect(last.line).toHaveLength(3)
  expect(last.points).toHaveLength(3) // base + 2 numbered stops
})

test('overview shows workload per day, budget lines with labels and uncovered interests', async () => {
  await planBeach()
  expect(screen.getByRole('meter', { name: /day 1 workload/i })).toHaveAttribute(
    'aria-valuenow',
    '60',
  )
  const budget = screen.getByRole('table', { name: /budget/i })
  expect(within(budget).getByText('Fort Aguada')).toBeInTheDocument()
  expect(within(budget).getByText('Verified')).toBeInTheDocument()
  const meals = within(budget).getByText('Meals').closest('tr')!
  expect(within(meals).getByText(/unknown/i)).toBeInTheDocument()
  expect(within(meals).queryByText(/₹0/)).toBeNull()
  expect(screen.getByText(/Not covered/)).toHaveTextContent(/nightlife/)
})

test('removing a stop replans with it avoided, the others kept and no refill', async () => {
  const fetch = await planBeach()
  await userEvent.click(screen.getByRole('button', { name: 'Remove Candolim Beach' }))
  await vi.waitFor(() => expect(generateCalls(fetch)).toHaveLength(2))
  const body = bodyOf(fetch, 1)
  expect(body.avoid).toEqual(['a'])
  expect(body.keepStops).toEqual([
    { placeId: 'b', day: 1 },
    { placeId: 'c', day: 2 },
  ])
  expect(body.fillFreeTime).toBe(false)
})

test('replace asks for another suggestion, move changes the day', async () => {
  const fetch = await planBeach()
  await userEvent.click(screen.getByRole('button', { name: 'Replace Fort Aguada' }))
  await vi.waitFor(() => expect(generateCalls(fetch)).toHaveLength(2))
  let body = bodyOf(fetch, 1)
  expect(body.avoid).toEqual(['b'])
  expect(body.fillFreeTime).toBe(true)
  await screen.findByRole('list', { name: /day 1 stops/i })

  await userEvent.selectOptions(screen.getByLabelText('Move Candolim Beach to day'), '2')
  await vi.waitFor(() => expect(generateCalls(fetch)).toHaveLength(3))
  body = bodyOf(fetch, 2)
  expect(body.keepStops).toContainEqual({ placeId: 'a', day: 2 })
  expect(body.fillFreeTime).toBe(false)
})

test('replan keeps only locked stops', async () => {
  const fetch = await planBeach()
  await userEvent.click(screen.getByRole('button', { name: 'Lock Fort Aguada' }))
  expect(screen.getByRole('button', { name: 'Lock Fort Aguada' })).toHaveAttribute(
    'aria-pressed',
    'true',
  )
  await userEvent.click(screen.getByRole('button', { name: /replan/i }))
  await vi.waitFor(() => expect(generateCalls(fetch)).toHaveLength(2))
  const body = bodyOf(fetch, 1)
  expect(body.keepStops).toEqual([{ placeId: 'b', day: 1 }])
  expect(body.fillFreeTime).toBe(true)
})

test('provider fallback is labelled when road routing is unavailable', async () => {
  const r = itineraryResponse()
  r.itinerary.travelProvider = 'ESTIMATE'
  r.itinerary.travelBasis = 'Straight-line estimate'
  mockApi([[/\/itineraries\/generate$/, () => r]])
  renderAt()
  await userEvent.click(screen.getByRole('checkbox', { name: /beach/i }))
  await userEvent.click(screen.getByRole('button', { name: /generate plan/i }))
  expect(await screen.findByText(/road routing unavailable/i)).toBeInTheDocument()
})

test('today mode plans one day starting today', async () => {
  const fetch = mockApi([[/\/itineraries\/generate$/, () => itineraryResponse()]])
  renderAt('/plan?mode=today')
  expect(screen.getByRole('heading', { name: /plan today/i })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('checkbox', { name: /heritage/i }))
  await userEvent.click(screen.getByRole('button', { name: /generate plan/i }))
  await screen.findByRole('list', { name: /day 1 stops/i })
  const body = bodyOf(fetch, 0)
  expect(body.days).toBe(1)
  expect(body.startDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
})

test('demo scenario fills the wizard and generates', async () => {
  const fetch = mockApi([[/\/itineraries\/generate$/, () => itineraryResponse()]])
  renderAt('/plan?demo=goa-beach-weekend')
  await screen.findByRole('list', { name: /day 1 stops/i })
  expect(screen.getByText(/Demo: Goa beach weekend/)).toBeInTheDocument()
  const body = bodyOf(fetch, 0)
  expect(body.interests.beach).toBe(5)
  expect(body.days).toBe(2)
})

test('shows infeasible-plan conflicts and relaxations', async () => {
  mockApi([
    [
      /\/itineraries\/generate$/,
      () =>
        new Response(
          JSON.stringify({
            errorCode: 'INFEASIBLE_PLAN',
            message: 'The hard constraints cannot all be met.',
            details: {
              conflicts: ['Museum cannot fit on any trip day: opening hours.'],
              suggestedRelaxations: ['Add a day or extend the day window.'],
            },
          }),
          { status: 422 },
        ),
    ],
  ])
  renderAt()
  await userEvent.click(screen.getByRole('checkbox', { name: /heritage/i }))
  await userEvent.click(screen.getByRole('button', { name: /generate plan/i }))
  expect(await screen.findByText(/Museum cannot fit/)).toBeInTheDocument()
  expect(screen.getByText(/Add a day/)).toBeInTheDocument()
})

test('needs at least one interest', () => {
  renderAt()
  expect(screen.getByRole('button', { name: /generate plan/i })).toBeDisabled()
})

test('signed-in users save the plan as a trip', async () => {
  const fetch = mockApi([
    [/\/itineraries\/generate$/, () => itineraryResponse()],
    [/\/trips$/, () => ({ id: 't9' })],
  ])
  render(
    <AuthContext
      value={{
        user: { id: 'u1', username: 'asha', displayName: null },
        loading: false,
        setUser: () => {},
      }}
    >
      <MemoryRouter initialEntries={['/plan']}>
        <Routes>
          <Route path="/plan" element={<PlanPage />} />
          <Route path="/trips/:id" element={<p>Saved trip page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext>,
  )
  await userEvent.click(screen.getByRole('checkbox', { name: /beach/i }))
  await userEvent.click(screen.getByRole('button', { name: /generate plan/i }))
  await userEvent.click(await screen.findByRole('button', { name: /save trip/i }))
  expect(await screen.findByText('Saved trip page')).toBeInTheDocument()
  const save = fetch.mock.calls.find((c) => String(c[0]).endsWith('/trips'))!
  const body = JSON.parse(String(save[1]?.body))
  expect(body.kind).toBe('ITINERARY')
  expect(body.title).toMatch(/2-day trip/)
  expect(body.request.interests).toEqual({ beach: 3 })
  expect(body.result.days).toHaveLength(2)
})

test('signed-out users are asked to sign in before saving', async () => {
  await planBeach()
  await userEvent.click(screen.getByRole('button', { name: /sign in to save/i }))
  expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /generate plan/i })).toBeInTheDocument()
})
