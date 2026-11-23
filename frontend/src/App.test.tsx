import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import App from './App'

afterEach(() => vi.unstubAllGlobals())

test('shows connected when health is UP', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify({ status: 'UP' }), { status: 200 })),
  )
  render(<App />)
  expect(await screen.findByText(/connected/)).toBeInTheDocument()
})

test('shows unavailable when API errors', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () =>
        new Response(JSON.stringify({ errorCode: 'INTERNAL_ERROR', message: 'x' }), {
          status: 500,
        }),
    ),
  )
  render(<App />)
  expect(await screen.findByText(/unavailable/)).toBeInTheDocument()
})
