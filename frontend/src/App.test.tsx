import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import App from './App'
import { mockApi } from './test/mockApi'

vi.mock('./maps/PlaceMap', () => ({ default: () => <div /> }))
afterEach(() => vi.unstubAllGlobals())

test('shows connected when health is UP', async () => {
  mockApi([
    [/\/health/, () => ({ status: 'UP' })],
    [/\/destinations/, () => []],
    [/\/places\/search/, () => ({ items: [], limit: 30, offset: 0, hasMore: false })],
  ])
  render(<App />)
  expect(await screen.findByText(/API: connected/)).toBeInTheDocument()
  expect(screen.getByText(/OpenStreetMap contributors/)).toBeInTheDocument()
})

test('shows unavailable when API errors', async () => {
  mockApi([[/./, () => new Response('{}', { status: 500 })]])
  render(<App />)
  expect(await screen.findByText(/API: unavailable/)).toBeInTheDocument()
})
