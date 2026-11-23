import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { expect, test } from 'vitest'
import { HomePage } from './HomePage'

test('offers the three planning modes and the demo scenarios', () => {
  render(
    <MemoryRouter>
      <HomePage />
    </MemoryRouter>,
  )
  expect(screen.getByRole('link', { name: /plan today/i })).toHaveAttribute(
    'href',
    '/plan?mode=today',
  )
  expect(screen.getByRole('link', { name: /multi-day trip/i })).toHaveAttribute(
    'href',
    '/plan?mode=trip',
  )
  expect(screen.getByRole('link', { name: /road trip/i })).toHaveAttribute('href', '/road-trip')
  expect(screen.getByRole('link', { name: /Goa beach weekend/ })).toHaveAttribute(
    'href',
    '/plan?demo=goa-beach-weekend',
  )
  expect(screen.getByRole('link', { name: /Mumbai to Goa/ })).toHaveAttribute(
    'href',
    '/road-trip?demo=mumbai-goa',
  )
})
