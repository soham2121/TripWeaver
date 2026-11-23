import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, test, vi } from 'vitest'
import { detail, summary } from '../../test/fixtures'
import { mockApi } from '../../test/mockApi'
import { roadTripResponse } from '../../test/planFixtures'
import { RoadTripPage } from './RoadTripPage'

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

const renderAt = (url = '/road-trip') =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <RoadTripPage />
    </MemoryRouter>,
  )

const ratnagiri = summary({ id: 'w1', name: 'Ratnagiri Bus Stand', lat: 16.99, lon: 73.3 })

test('plans the default Mumbai to Goa trip and shows days, overnight, detours and costs', async () => {
  const fetch = mockApi([[/\/road-trips\/plan$/, () => roadTripResponse()]])
  renderAt()
  await userEvent.click(screen.getByRole('checkbox', { name: /heritage/i }))
  await userEvent.click(screen.getByRole('button', { name: /plan road trip/i }))

  const day1 = await screen.findByRole('list', { name: /day 1 timeline/i })
  expect(within(day1).getByText(/Magen David Synagogue/)).toBeInTheDocument()
  expect(within(day1).getByText(/Rest break/)).toBeInTheDocument()
  expect(screen.getByText(/Suggested stay: Manjunath/)).toBeInTheDocument()
  expect(screen.getByText(/availability and price unknown/)).toBeInTheDocument()
  expect(screen.getByText(/\+11 min/)).toBeInTheDocument()
  expect(screen.getByText(/not covered by any detour/)).toBeInTheDocument()
  const budget = screen.getByRole('table', { name: /budget/i })
  expect(within(budget).getByText('Estimate')).toBeInTheDocument()
  expect(
    within(within(budget).getByText('Tolls').closest('tr')!).getByText(/unknown/i),
  ).toBeTruthy()
  await screen.findByTestId('map')
  expect(mapProps.at(-1)!.line).toHaveLength(2)

  const body = JSON.parse(String(fetch.mock.calls[0][1]?.body))
  expect(body.origin.name).toMatch(/Mumbai/)
  expect(body.destination.name).toMatch(/Candolim/)
  expect(body.interests).toEqual({ heritage: 3 })
  expect(body.maxDrivingHoursPerDay).toBe(7)
})

test('adds an overnight waypoint from search', async () => {
  const fetch = mockApi([
    [
      /\/places\/autocomplete/,
      () => [
        {
          type: 'PLACE',
          label: 'Ratnagiri Bus Stand',
          value: 'w1',
          placeId: 'w1',
          detail: 'bus_station',
        },
      ],
    ],
    [/\/places\/w1$/, () => detail(ratnagiri)],
    [/\/road-trips\/plan$/, () => roadTripResponse()],
  ])
  renderAt()
  await userEvent.type(screen.getByRole('combobox', { name: /add a stop/i }), 'Ratn')
  await userEvent.click(await screen.findByRole('option', { name: /Ratnagiri/ }))
  await userEvent.click(
    await screen.findByRole('checkbox', { name: /stay overnight at Ratnagiri/i }),
  )
  await userEvent.click(screen.getByRole('button', { name: /plan road trip/i }))
  await screen.findByRole('list', { name: /day 1 timeline/i })
  const body = JSON.parse(String(fetch.mock.calls.at(-1)![1]?.body))
  expect(body.waypoints).toEqual([
    { name: 'Ratnagiri Bus Stand', placeId: 'w1', lat: 16.99, lon: 73.3, overnight: true },
  ])
})

test('explains an impossible arrival', async () => {
  mockApi([
    [
      /\/road-trips\/plan$/,
      () =>
        new Response(
          JSON.stringify({
            errorCode: 'INFEASIBLE_PLAN',
            message: 'The road trip cannot meet its hard limits.',
            details: {
              conflicts: ['Arrival after the requested time.'],
              suggestedRelaxations: ['Leave earlier or allow another day.'],
              estimatedArrival: '2026-12-15T11:44',
            },
          }),
          { status: 422 },
        ),
    ],
  ])
  renderAt()
  await userEvent.click(screen.getByRole('button', { name: /plan road trip/i }))
  expect(await screen.findByText(/Arrival after the requested time/)).toBeInTheDocument()
  expect(screen.getByText(/Estimated arrival: 2026-12-15 11:44/)).toBeInTheDocument()
  expect(screen.getByText(/Leave earlier/)).toBeInTheDocument()
})
