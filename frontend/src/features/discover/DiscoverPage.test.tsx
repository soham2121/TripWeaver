import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { mockApi } from '../../test/mockApi'
import { detail, goa, museum, summary } from '../../test/fixtures'
import { DiscoverPage } from './DiscoverPage'

vi.mock('../../maps/PlaceMap', () => ({ default: () => <div data-testid="map" /> }))
afterEach(() => vi.unstubAllGlobals())

function setup() {
  return mockApi([
    [/\/destinations$/, () => [goa]],
    [
      /\/places\/autocomplete/,
      () => [
        {
          type: 'INTEREST',
          label: 'heritage',
          value: 'heritage',
          placeId: null,
          detail: 'interest',
        },
        {
          type: 'PLACE',
          label: 'Archeological Museum',
          value: 'p2',
          placeId: 'p2',
          detail: 'museum',
        },
      ],
    ],
    [
      /\/places\/search/,
      () => ({ items: [summary(), museum], limit: 30, offset: 0, hasMore: false }),
    ],
    [/\/places\/p2$/, () => detail(museum)],
    [/\/places\/p1$/, () => detail(summary())],
  ])
}

test('lists places and never shows unknown cost as zero', async () => {
  setup()
  render(<DiscoverPage />)
  const list = await screen.findByRole('list')
  expect(await within(list).findByText('Baga Beach')).toBeInTheDocument()
  expect(within(list).getByText('Cost unknown')).toBeInTheDocument()
  expect(within(list).getByText(/₹10 \/ person/)).toBeInTheDocument()
  expect(within(list).getByText('Verified')).toBeInTheDocument()
  expect(list.textContent).not.toMatch(/₹0/)
})

test('autocomplete interest suggestion applies a filter', async () => {
  const fetch = setup()
  render(<DiscoverPage />)
  await userEvent.type(screen.getByRole('combobox', { name: 'Search places' }), 'heri')
  await userEvent.click(await screen.findByText('heritage', { selector: 'span' }))
  await waitFor(() =>
    expect(fetch.mock.calls.some(([u]) => String(u).includes('interest=heritage'))).toBe(true),
  )
})

test('selecting a place opens provenance details', async () => {
  setup()
  render(<DiscoverPage />)
  await userEvent.click(await screen.findByText('Archeological Museum'))
  const panel = await screen.findByRole('article', { name: /Details for Archeological Museum/ })
  expect(within(panel).getByText('Source')).toHaveAttribute('href', 'https://gov.example/x')
  expect(within(panel).getByText(/OpenStreetMap contributors/)).toBeInTheDocument()
})

test('place without cost explains unknown cost', async () => {
  setup()
  render(<DiscoverPage />)
  await userEvent.click(await screen.findByText('Baga Beach'))
  expect(
    await screen.findByText(/Cost unknown\. Not included in budgets as zero/),
  ).toBeInTheDocument()
  expect(screen.getByText('Estimate')).toBeInTheDocument()
})

test('shows an error state when the API fails', async () => {
  mockApi([[/./, () => new Response('{}', { status: 500 })]])
  render(<DiscoverPage />)
  expect(await screen.findByRole('alert')).toHaveTextContent(/Could not load places/)
})

test('map view uses the map component', async () => {
  setup()
  render(<DiscoverPage />)
  await userEvent.click(screen.getByRole('button', { name: 'Map' }))
  expect(await screen.findByTestId('map')).toBeInTheDocument()
})
