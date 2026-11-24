import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { expect, test, vi } from 'vitest'
import { returnFixture, roadTripResponse } from '../../test/planFixtures'
import type { RoadTrip } from '../../types/roadtrip'
import { mockApi } from '../../test/mockApi'
import { MemoryRouter } from 'react-router-dom'
import { RoadTripPage, RoadTripView } from './RoadTripPage'

vi.mock('../../maps/PlaceMap', () => ({ default: () => <div data-testid="map" /> }))

const withReturn = (chosen = 1): RoadTrip => ({
  ...roadTripResponse().roadTrip,
  returnTrip: returnFixture(chosen),
})

test('four options in order with the chosen one shown', () => {
  render(<RoadTripView trip={withReturn()} onPickReturn={() => {}} />)
  expect(screen.getByRole('heading', { name: 'Way home' })).toBeInTheDocument()
  const radios = screen.getAllByRole('radio')
  expect(radios.map((r) => r.getAttribute('aria-label'))).toEqual([
    expect.stringMatching(/^1\. Direct/),
    expect.stringMatching(/^2\. Same way back/),
    expect.stringMatching(/^3\. Same road, new stops/),
    expect.stringMatching(/^4\. Through/),
  ])
  expect(radios[0]).toBeChecked()
  expect(screen.getByRole('heading', { name: /^Return day 1/ })).toBeInTheDocument()
})

test('an unbuildable option is disabled with its reason', () => {
  render(<RoadTripView trip={withReturn()} onPickReturn={() => {}} />)
  expect(screen.getAllByRole('radio')[3]).toBeDisabled()
  expect(
    screen.getByText('No town off the outbound road within a third more driving'),
  ).toBeInTheDocument()
})

test('picking switches the shown route without a request', async () => {
  const fetch = vi.fn()
  vi.stubGlobal('fetch', fetch)
  const pick = vi.fn()
  function Host() {
    const [trip, setTrip] = useState(withReturn())
    return (
      <RoadTripView
        trip={trip}
        onPickReturn={(n) => {
          pick(n)
          setTrip({ ...trip, returnTrip: { ...trip.returnTrip!, chosen: n } })
        }}
      />
    )
  }
  render(<Host />)
  await userEvent.click(screen.getAllByRole('radio')[1])
  expect(pick).toHaveBeenCalledWith(2)
  expect(screen.getAllByRole('radio')[1]).toBeChecked()
  expect(fetch).not.toHaveBeenCalled()
  vi.unstubAllGlobals()
})

test('viewers see the picked route but cannot switch', () => {
  render(<RoadTripView trip={withReturn(2)} />)
  const radios = screen.getAllByRole('radio')
  expect(radios.every((r) => (r as HTMLInputElement).disabled)).toBe(true)
  expect(radios[1]).toBeChecked()
  expect(screen.getByRole('heading', { name: /^Return day 1/ })).toBeInTheDocument()
})

test('old trips without a return render as before', () => {
  render(<RoadTripView trip={roadTripResponse().roadTrip} />)
  expect(screen.queryByRole('heading', { name: 'Way home' })).not.toBeInTheDocument()
})

test('cards count fixed stops as well as detours', () => {
  const trip = withReturn()
  const same = trip.returnTrip!.options[1]
  const plan = structuredClone(same.plan!)
  plan.days[0].items.splice(2, 0, { ...plan.days[0].items[2], type: 'WAYPOINT', name: 'Ratnagiri' })
  trip.returnTrip!.options[1] = { ...same, plan }
  render(<RoadTripView trip={trip} onPickReturn={() => {}} />)
  const card = screen.getAllByRole('radio')[1].closest('label')!
  expect(card).toHaveTextContent('2 stop(s)')
})

test('the picked way home shows its own budget', () => {
  render(<RoadTripView trip={withReturn()} onPickReturn={() => {}} />)
  expect(screen.getByRole('heading', { name: 'Budget for the way home' })).toBeInTheDocument()
})

test('the pick survives a re-plan', async () => {
  const fetch = mockApi([
    [/\/road-trips\/plan$/, () => ({ ...roadTripResponse(), roadTrip: withReturn() })],
  ])
  render(
    <MemoryRouter initialEntries={['/road-trip?demo=mumbai-goa']}>
      <RoadTripPage />
    </MemoryRouter>,
  )
  await screen.findByRole('heading', { name: 'Way home' })
  await userEvent.click(screen.getAllByRole('radio')[2])
  await userEvent.click(screen.getByRole('button', { name: /plan road trip/i }))
  await screen.findByRole('heading', { name: 'Way home' })
  const sent = JSON.parse(String(fetch.mock.calls.at(-1)![1]?.body))
  expect(sent.returnChoice).toBe(3)
})
