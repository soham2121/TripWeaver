import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { mockApi } from '../../test/mockApi'
import { summary } from '../../test/fixtures'
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

const stop = (order: number, name: string, id: string) => ({
  order,
  place: { ...summary(), id, name },
  arrive: '09:10:00',
  start: '09:10:00',
  end: '10:10:00',
  visitMinutes: 60,
  visitBasis: 'balanced pace from the 60–90 min category default range',
  travelMinutes: 10,
  travelKm: 3.2,
  waitMinutes: 0,
  kind: 'COVER',
  covers: ['beach'],
  cost: { min: null, max: null, unknown: true, confidence: 'UNKNOWN', note: 'unknown' },
  hours: { raw: null, confidence: 'UNKNOWN', status: 'UNKNOWN' },
  reason: 'covers beach',
})

const itinerary = {
  runId: 'r1',
  durationMicros: 1000,
  itinerary: {
    days: [
      {
        day: 1,
        date: '2026-12-14',
        dayOfWeek: 'MONDAY',
        start: '09:00:00',
        end: '19:00:00',
        pace: 'BALANCED',
        returnToBase: true,
        stops: [stop(1, 'Candolim Beach', 'a'), stop(2, 'Fort Aguada', 'b')],
        meals: [{ label: 'Lunch', start: '12:30:00', end: '13:30:00', place: null }],
        returnTravelMinutes: 12,
        endOfDay: '16:00:00',
        workload: { utilisation: 0.6, freeMinutes: 90, travelMinutes: 40, label: 'BALANCED' },
        cost: { min: 0, max: 0, unknown: true, confidence: 'INCOMPLETE', note: '' },
        warnings: [],
        ordering: {
          applied: 'DOUBLE_TREE',
          method: 'double-tree',
          insertionCost: 50,
          doubleTreeCost: 44,
          mstCost: 30,
          nearestNeighbourCost: 46,
          exactCost: 44,
          metric: true,
          guaranteeApplies: true,
          metricViolations: [],
          note: 'Double-Tree order saves 6 min over the insertion order.',
        },
        route: {
          provider: 'OSRM',
          basis: 'OSRM road network',
          geometry: [
            [73.76, 15.52],
            [73.77, 15.5],
            [73.76, 15.52],
          ],
          legs: [],
          totalMinutes: 40,
          totalKm: 12.4,
          warnings: [],
        },
        navigationUrls: ['https://www.google.com/maps/dir/?api=1&origin=15.52,73.76'],
      },
    ],
    budget: { status: 'INCOMPLETE', knownMax: 0, unknownItems: ['Food'], complete: false },
    coverage: { requested: ['beach'], covered: ['beach'], uncovered: [], reasons: {} },
    unscheduled: [],
    repairs: [],
    warnings: ['1 cost item(s) are unknown, so totals are incomplete.'],
    travelProvider: 'OSRM',
    travelBasis: 'OSRM road network (OpenStreetMap, car profile) + 5 min per leg',
    disclaimer: 'Day allocation is a greedy insertion heuristic.',
    seed: 42,
  },
}

test('generates a plan and shows numbered stops, route, ordering and navigation', async () => {
  const fetch = mockApi([[/\/itineraries\/generate$/, () => itinerary]])
  render(<PlanPage />)
  await userEvent.click(screen.getByRole('checkbox', { name: /beach/i }))
  await userEvent.click(screen.getByRole('button', { name: /generate plan/i }))

  const list = await screen.findByRole('list', { name: /day 1 stops/i })
  expect(within(list).getByText(/1\. Candolim Beach/)).toBeInTheDocument()
  expect(within(list).getByText(/2\. Fort Aguada/)).toBeInTheDocument()
  expect(screen.getByText(/Road routing: OSRM/)).toBeInTheDocument()
  expect(screen.getByText(/≤ 2 × optimal/)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /open in google maps/i })).toHaveAttribute(
    'href',
    itinerary.itinerary.days[0].navigationUrls[0],
  )
  expect(screen.getByText(/Lunch 12:30/)).toBeInTheDocument()

  await screen.findByTestId('map')
  const last = mapProps.at(-1)!
  expect(last.line).toHaveLength(3)
  expect(last.points).toHaveLength(3) // base + 2 numbered stops

  const body = JSON.parse(String(fetch.mock.calls[0][1]?.body))
  expect(body.interests).toEqual({ beach: 3 })
  expect(body.baseLat).toBeCloseTo(15.5202)
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
  render(<PlanPage />)
  await userEvent.click(screen.getByRole('checkbox', { name: /heritage/i }))
  await userEvent.click(screen.getByRole('button', { name: /generate plan/i }))
  expect(await screen.findByText(/Museum cannot fit/)).toBeInTheDocument()
  expect(screen.getByText(/Add a day/)).toBeInTheDocument()
})

test('needs at least one interest', async () => {
  render(<PlanPage />)
  expect(screen.getByRole('button', { name: /generate plan/i })).toBeDisabled()
})
