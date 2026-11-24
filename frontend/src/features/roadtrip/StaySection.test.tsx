import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { expect, test, vi } from 'vitest'
import { mockApi } from '../../test/mockApi'
import { roadTripResponse, stayFixture } from '../../test/planFixtures'
import { RoadTripPage, RoadTripView } from './RoadTripPage'

vi.mock('../../maps/PlaceMap', () => ({ default: () => <div data-testid="map" /> }))

test('stay section shows the planned days read-only', () => {
  render(<RoadTripView trip={{ ...roadTripResponse().roadTrip, stay: stayFixture() }} />)
  expect(screen.getByRole('heading', { name: /^Your stay in Candolim/ })).toBeInTheDocument()
  expect(
    screen.getByText('2 night(s) at Candolim · priced in the trip budget above'),
  ).toBeInTheDocument()
  expect(screen.getByRole('tab', { name: /^Day 1/ })).toBeInTheDocument()
  expect(screen.getByRole('tab', { name: /^Day 2/ })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Lock' })).not.toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: 'Save as PDF' })).toHaveLength(1)
})

test('a stay without place data shows the note', () => {
  const note = 'No place data near Nashik: the stay is counted, but its days are not planned.'
  render(
    <RoadTripView
      trip={{
        ...roadTripResponse().roadTrip,
        stay: { ...stayFixture(), destination: null, itinerary: null, note },
      }}
    />,
  )
  expect(screen.getByText(note)).toBeInTheDocument()
  expect(screen.queryByRole('tab', { name: /^Day 1/ })).not.toBeInTheDocument()
})

test('old trips without a stay render as before', () => {
  render(<RoadTripView trip={roadTripResponse().roadTrip} />)
  expect(screen.queryByText(/Your stay in/)).not.toBeInTheDocument()
})

test('the Mumbai to Goa demo asks for two days at the destination', async () => {
  const fetch = mockApi([[/\/road-trips\/plan$/, () => roadTripResponse()]])
  render(
    <MemoryRouter initialEntries={['/road-trip?demo=mumbai-goa']}>
      <RoadTripPage />
    </MemoryRouter>,
  )
  await screen.findByRole('list', { name: /day 1 timeline/i })
  const sent = JSON.parse(String(fetch.mock.calls[0][1]?.body))
  expect(sent.stayDays).toBe(2)
  expect(sent.planReturn).toBe(true)
  expect(screen.getByRole('spinbutton', { name: /Days at destination/ })).toHaveValue(2)
})
