import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, test, vi } from 'vitest'
import { mockApi } from '../../test/mockApi'
import type { AppUser } from '../../types/trip'
import { AuthContext } from '../auth/auth'
import { SaveTrip } from './SaveTrip'

afterEach(() => vi.unstubAllGlobals())

function Harness() {
  const [user, setUser] = useState<AppUser | null>(null)
  return (
    <AuthContext value={{ user, loading: false, setUser }}>
      <MemoryRouter initialEntries={['/plan?mode=today']}>
        <p>Plan on screen</p>
        <SaveTrip kind="ITINERARY" request={{}} result={{}} defaultTitle="Panaji" />
      </MemoryRouter>
    </AuthContext>
  )
}

test('signing in to save keeps the plan on screen and then offers to save', async () => {
  mockApi([[/\/auth\/login$/, () => ({ id: 'u1', username: 'asha', displayName: 'Asha' })]])
  render(<Harness />)
  await userEvent.click(screen.getByRole('button', { name: 'Sign in to save this trip' }))
  await userEvent.type(screen.getByLabelText('Username'), 'asha')
  await userEvent.type(screen.getByLabelText('Password'), 'goa-trip-2026')
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByRole('button', { name: 'Save trip' })).toBeInTheDocument()
  expect(screen.getByText('Plan on screen')).toBeInTheDocument()
  expect(screen.getByLabelText('Trip name')).toHaveValue('Panaji')
})
