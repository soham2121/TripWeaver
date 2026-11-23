import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, test, vi } from 'vitest'
import { mockApi } from '../../test/mockApi'
import { itineraryResponse } from '../../test/planFixtures'
import type { Role, Trip } from '../../types/trip'
import { SharedPage } from './SharedPage'
import { TripPage } from './TripPage'

vi.mock('../../maps/PlaceMap', () => ({ default: () => <div data-testid="map" /> }))
afterEach(() => vi.unstubAllGlobals())

const trip = (role: Role, version = 3): Trip => ({
  id: 't1',
  title: 'Goa with friends',
  kind: 'ITINERARY',
  role,
  owner: { id: 'u1', username: 'asha', displayName: 'Asha' },
  members: [
    { userId: 'u2', username: 'ravi', displayName: null, role: 'EDITOR' },
    { userId: 'u3', username: 'meera', displayName: null, role: 'VIEWER' },
  ],
  version,
  request: {
    baseLat: 15.52,
    baseLon: 73.77,
    days: 2,
    partySize: 2,
    interests: { beach: 5 },
  } as never,
  result: itineraryResponse().itinerary,
  createdAt: '2026-11-23T10:00:00Z',
  updatedAt: '2026-11-23T10:05:00Z',
})

const suggestion = {
  id: 's1',
  placeId: 'p9',
  placeName: 'Chapora Fort',
  day: 1,
  note: 'Sunset views',
  suggestedBy: 'ravi',
  createdAt: '2026-11-23T10:00:00Z',
  up: 2,
  down: 0,
  score: 2,
  myVote: 0,
}

const side: [RegExp, () => unknown][] = [
  [/\/trips\/t1\/suggestions$/, () => [suggestion]],
  [
    /\/trips\/t1\/history$/,
    () => [
      {
        action: 'CREATED',
        summary: 'Created "Goa with friends"',
        username: 'asha',
        version: 0,
        at: '2026-11-23T10:00:00Z',
      },
    ],
  ],
  [
    /\/trips\/t1\/expenses$/,
    () => ({
      items: [
        {
          id: 'e1',
          description: 'Scooter hire',
          amount: 3000,
          paidById: 'u1',
          paidBy: 'asha',
          createdAt: '',
        },
      ],
      total: 3000,
      perPerson: 1000,
      balances: [
        { userId: 'u1', username: 'asha', paid: 3000, share: 1000, balance: 2000 },
        { userId: 'u2', username: 'ravi', paid: 0, share: 1000, balance: -1000 },
        { userId: 'u3', username: 'meera', paid: 0, share: 1000, balance: -1000 },
      ],
    }),
  ],
  [/\/trips\/t1\/share-links$/, () => []],
]

const renderTrip = () =>
  render(
    <MemoryRouter initialEntries={['/trips/t1']}>
      <Routes>
        <Route path="/trips/:id" element={<TripPage />} />
        <Route path="/trips" element={<p>My trips</p>} />
      </Routes>
    </MemoryRouter>,
  )

const calls = (fetch: ReturnType<typeof mockApi>, method: string, re: RegExp) =>
  fetch.mock.calls.filter((c) => (c[1]?.method ?? 'GET') === method && re.test(String(c[0])))

test('owner sees members and can create and revoke a read-only link', async () => {
  const fetch = mockApi([
    [/\/trips\/t1\/share-links\/l1$/, () => new Response(null, { status: 204 })],
    [
      /\/trips\/t1\/share-links$/,
      (_u, init) =>
        init?.method === 'POST'
          ? {
              id: 'l1',
              token: 'TOKEN123',
              createdAt: '',
              expiresAt: null,
              revokedAt: null,
              active: true,
            }
          : [
              {
                id: 'l1',
                token: null,
                createdAt: '2026-11-23T10:00:00Z',
                expiresAt: null,
                revokedAt: null,
                active: true,
              },
            ],
    ],
    ...side,
    [/\/trips\/t1$/, () => trip('OWNER')],
  ])
  renderTrip()
  expect(await screen.findByRole('heading', { name: 'Goa with friends' })).toBeInTheDocument()
  expect(screen.getByText(/you are the owner/i)).toBeInTheDocument()
  const members = screen.getByRole('region', { name: /people/i })
  expect(within(members).getByText('ravi')).toBeInTheDocument()
  expect(within(members).getByRole('button', { name: /add/i })).toBeInTheDocument()

  await userEvent.click(screen.getByRole('button', { name: /create read-only link/i }))
  expect(await screen.findByDisplayValue(/\/shared\/TOKEN123$/)).toBeInTheDocument()
  await userEvent.click(await screen.findByRole('button', { name: /revoke/i }))
  await vi.waitFor(() => expect(calls(fetch, 'DELETE', /share-links\/l1$/)).toHaveLength(1))
  await vi.waitFor(() => expect(screen.queryByDisplayValue(/TOKEN123/)).toBeNull())
})

test('viewer gets a read-only itinerary without edit, member or share controls', async () => {
  mockApi([...side, [/\/trips\/t1$/, () => trip('VIEWER')]])
  renderTrip()
  expect(await screen.findByRole('list', { name: /day 1 stops/i })).toBeInTheDocument()
  expect(screen.getByText(/you are a viewer/i)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /remove candolim beach/i })).toBeNull()
  expect(screen.queryByRole('button', { name: /create read-only link/i })).toBeNull()
  expect(screen.queryByRole('button', { name: /vote for/i })).toBeNull()
  expect(screen.queryByRole('button', { name: /add expense/i })).toBeNull()
  expect(screen.getByText('Sunset views')).toBeInTheDocument()
})

test('editor edit saves through the trip with the version it was based on', async () => {
  const fetch = mockApi([
    ...side,
    [/\/trips\/t1\/generate$/, () => trip('EDITOR', 4)],
    [/\/trips\/t1$/, () => trip('EDITOR')],
  ])
  renderTrip()
  await userEvent.click(await screen.findByRole('button', { name: 'Remove Candolim Beach' }))
  await vi.waitFor(() => expect(calls(fetch, 'POST', /\/generate$/)).toHaveLength(1))
  const body = JSON.parse(String(calls(fetch, 'POST', /\/generate$/)[0][1]?.body))
  expect(body.version).toBe(3)
  expect(body.request.avoid).toEqual(['a'])
  expect(body.request.fillFreeTime).toBe(false)
  expect(body.request.baseLat).toBe(15.52)
  expect(await screen.findByText(/version 4/i)).toBeInTheDocument()
})

test('a stale edit shows the conflict and loads the latest version', async () => {
  const fetch = mockApi([
    ...side,
    [
      /\/trips\/t1\/generate$/,
      () =>
        new Response(
          JSON.stringify({
            errorCode: 'VERSION_CONFLICT',
            message: 'Someone else changed this trip. Reload it and apply your change again.',
            details: { currentVersion: 4 },
          }),
          { status: 409 },
        ),
    ],
    [/\/trips\/t1$/, () => trip('EDITOR', calls(fetch, 'POST', /\/generate$/).length ? 4 : 3)],
  ])
  renderTrip()
  await userEvent.click(await screen.findByRole('button', { name: 'Remove Candolim Beach' }))
  expect(await screen.findByRole('alert')).toHaveTextContent(/someone else changed this trip/i)
  expect(await screen.findByText(/version 4/i)).toBeInTheDocument()
})

test('editors vote on suggestions and add shared expenses', async () => {
  const fetch = mockApi([
    [/\/suggestions\/s1\/votes$/, () => new Response(null, { status: 201 })],
    [
      /\/trips\/t1\/expenses$/,
      (_u, init) => (init?.method === 'POST' ? { id: 'e2' } : side[2][1]()),
    ],
    ...side,
    [/\/trips\/t1$/, () => trip('EDITOR')],
  ])
  renderTrip()
  await userEvent.click(await screen.findByRole('button', { name: 'Vote for Chapora Fort' }))
  await vi.waitFor(() => expect(calls(fetch, 'POST', /votes$/)).toHaveLength(1))
  expect(JSON.parse(String(calls(fetch, 'POST', /votes$/)[0][1]?.body))).toEqual({ value: 1 })

  const balances = screen.getByRole('table', { name: /balances/i })
  expect(within(balances).getByText('asha').closest('tr')).toHaveTextContent(/is owed ₹2,000/)
  await userEvent.type(screen.getByLabelText(/what for/i), 'Lunch')
  await userEvent.type(screen.getByLabelText(/amount/i), '600')
  await userEvent.click(screen.getByRole('button', { name: /add expense/i }))
  await vi.waitFor(() => expect(calls(fetch, 'POST', /expenses$/)).toHaveLength(1))
  expect(JSON.parse(String(calls(fetch, 'POST', /expenses$/)[0][1]?.body))).toEqual({
    description: 'Lunch',
    amount: 600,
  })
})

test('shared link shows the trip read-only and explains a dead link', async () => {
  mockApi([
    [
      /\/shared\/good$/,
      () => ({
        title: 'Goa with friends',
        kind: 'ITINERARY',
        result: itineraryResponse().itinerary,
        updatedAt: '2026-11-23T10:05:00Z',
      }),
    ],
    [
      /\/shared\/dead$/,
      () =>
        new Response(
          JSON.stringify({
            errorCode: 'SHARE_LINK_INVALID',
            message: 'This link is invalid, revoked or expired',
          }),
          { status: 404 },
        ),
    ],
  ])
  const at = (url: string) =>
    render(
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/shared/:token" element={<SharedPage />} />
        </Routes>
      </MemoryRouter>,
    )
  const first = at('/shared/good')
  expect(await screen.findByRole('heading', { name: 'Goa with friends' })).toBeInTheDocument()
  expect(screen.getByText(/read-only/i)).toBeInTheDocument()
  expect(screen.getByRole('list', { name: /day 1 stops/i })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /remove/i })).toBeNull()
  first.unmount()
  at('/shared/dead')
  expect(await screen.findByText(/invalid, revoked or expired/i)).toBeInTheDocument()
})
