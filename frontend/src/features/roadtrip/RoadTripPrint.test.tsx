import { act, render, screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import { returnFixture, roadTripResponse, stayFixture } from '../../test/planFixtures'
import { RoadTripView } from './RoadTripPage'

test('the printable road trip lists every day, item and Maps link', () => {
  const trip = roadTripResponse().roadTrip
  render(<RoadTripView trip={trip} />)
  expect(screen.getByRole('button', { name: 'Save as PDF' })).toBeInTheDocument()
  act(() => {
    window.dispatchEvent(new Event('beforeprint'))
  })
  const s = within(screen.getByRole('region', { name: 'Printable plan' }))
  expect(
    s.getByRole('heading', {
      name: `Road trip: ${trip.segments[0].from} to ${trip.segments.at(-1)!.to}`,
    }),
  ).toBeInTheDocument()
  expect(s.getByRole('heading', { name: /^Day 1/ })).toBeInTheDocument()
  expect(s.getByRole('heading', { name: /^Day 2/ })).toBeInTheDocument()
  const items = s.getAllByRole('listitem').map((li) => li.textContent)
  for (const t of [
    '07:00 Leave Mumbai (CSMT)',
    'Detour: Magen David Synagogue',
    'Overnight: Manjunath',
    'Arrive Candolim',
  ])
    expect(items.some((x) => x?.includes(t))).toBe(true)
  expect(s.getByRole('link', { name: 'Open day 2 in Google Maps' })).toHaveAttribute(
    'href',
    trip.days[1].navigationUrls[0],
  )
})

test('the on-screen timeline shows each item time from the API LocalTime', () => {
  render(<RoadTripView trip={roadTripResponse().roadTrip} />)
  const day1 = within(screen.getByRole('list', { name: 'Day 1 timeline' }))
  expect(day1.getAllByRole('listitem')[0]).toHaveTextContent(/^07:00\s*Leave Mumbai \(CSMT\)/)
})

test('the road-trip PDF lists the stay days after the drive in one sheet', () => {
  render(<RoadTripView trip={{ ...roadTripResponse().roadTrip, stay: stayFixture() }} />)
  act(() => {
    window.dispatchEvent(new Event('beforeprint'))
  })
  const sheets = screen.getAllByRole('region', { name: 'Printable plan' })
  expect(sheets).toHaveLength(1)
  const headings = within(sheets[0])
    .getAllByRole('heading', { level: 2 })
    .map((h) => h.textContent ?? '')
    .filter((t) => !t.startsWith('Budget'))
  expect(headings.map((t) => t.split(' ·')[0])).toEqual([
    'Day 1',
    'Day 2',
    'Your stay in Candolim',
    'Stay day 1',
    'Stay day 2',
  ])
  expect(
    within(sheets[0]).getByRole('link', { name: 'Open stay day 1 in Google Maps' }),
  ).toBeInTheDocument()
})

test('the PDF prints the picked way home after the stay', () => {
  render(
    <RoadTripView
      trip={{ ...roadTripResponse().roadTrip, stay: stayFixture(), returnTrip: returnFixture(2) }}
    />,
  )
  act(() => {
    window.dispatchEvent(new Event('beforeprint'))
  })
  const sheets = screen.getAllByRole('region', { name: 'Printable plan' })
  expect(sheets).toHaveLength(1)
  const headings = within(sheets[0])
    .getAllByRole('heading', { level: 2 })
    .map((h) => (h.textContent ?? '').split(' ·')[0])
    .filter((t) => !t.startsWith('Budget'))
  expect(headings).toEqual([
    'Day 1',
    'Day 2',
    'Your stay in Candolim',
    'Stay day 1',
    'Stay day 2',
    'Way home: Same way back',
    'Return day 1',
    'Return day 2',
  ])
  expect(
    within(sheets[0]).getByRole('link', { name: 'Open return day 1 in Google Maps' }),
  ).toBeInTheDocument()
})
