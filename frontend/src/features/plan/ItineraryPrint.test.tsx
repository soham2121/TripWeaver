import { act, render, screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import { itineraryResponse } from '../../test/planFixtures'
import { ItineraryView } from './ItineraryView'

/** Opens the print view (as the browser does before printing) and returns the printable sheet. */
const sheet = () => {
  act(() => {
    window.dispatchEvent(new Event('beforeprint'))
  })
  return within(screen.getByRole('region', { name: 'Printable plan' }))
}

test('the printable itinerary lists every day, stop, meal and Maps link', () => {
  const it = itineraryResponse().itinerary
  render(
    <ItineraryView it={it} base={{ lat: 15.52, lon: 73.76 }} title="2-day trip from Candolim" />,
  )
  expect(screen.getByRole('button', { name: 'Save as PDF' })).toBeInTheDocument()
  const s = sheet()
  expect(s.getByRole('heading', { name: '2-day trip from Candolim' })).toBeInTheDocument()
  expect(s.getByRole('heading', { name: /^Day 1/ })).toBeInTheDocument()
  expect(s.getByRole('heading', { name: /^Day 2/ })).toBeInTheDocument()
  const items = s.getAllByRole('listitem').map((li) => li.textContent)
  for (const n of ['Candolim Beach', 'Fort Aguada', 'Calangute Beach'])
    expect(items.some((x) => x?.includes(n))).toBe(true)
  expect(s.getAllByText(/Lunch 12:30–13:30/)).toHaveLength(2)
  expect(s.getByRole('link', { name: 'Open day 1 in Google Maps' })).toHaveAttribute(
    'href',
    it.days[0].navigationUrls[0],
  )
})

test('a day split into several Maps links prints every part', () => {
  const it = itineraryResponse().itinerary
  it.days[0].navigationUrls = [
    'https://www.google.com/maps/dir/?api=1&a',
    'https://www.google.com/maps/dir/?api=1&b',
  ]
  render(<ItineraryView it={it} base={{ lat: 15.52, lon: 73.76 }} />)
  const s = sheet()
  expect(s.getByRole('link', { name: 'Open day 1 in Google Maps (part 2)' })).toHaveAttribute(
    'href',
    'https://www.google.com/maps/dir/?api=1&b',
  )
  expect(s.getByRole('heading', { name: '2-day trip' })).toBeInTheDocument()
})
