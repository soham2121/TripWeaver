import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, test, vi } from 'vitest'
import { mockApi } from '../../test/mockApi'
import { AuthContext } from './auth'
import { LoginPage } from './LoginPage'

afterEach(() => vi.unstubAllGlobals())

const renderLogin = (setUser = vi.fn()) => {
  render(
    <AuthContext value={{ user: null, loading: false, setUser }}>
      <MemoryRouter initialEntries={['/login?next=/plan']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/plan" element={<p>Plan page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext>,
  )
  return setUser
}

const asha = { id: 'u1', username: 'asha', displayName: 'Asha' }

test('signs in and returns to where the user came from', async () => {
  const fetch = mockApi([[/\/auth\/login$/, () => asha]])
  const setUser = renderLogin()
  await userEvent.type(screen.getByLabelText('Username'), 'asha')
  await userEvent.type(screen.getByLabelText('Password'), 'goa-trip-2026')
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByText('Plan page')).toBeInTheDocument()
  expect(setUser).toHaveBeenCalledWith(asha)
  expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({
    username: 'asha',
    password: 'goa-trip-2026',
  })
})

test('creates an account', async () => {
  const fetch = mockApi([[/\/auth\/register$/, () => asha]])
  renderLogin()
  await userEvent.click(screen.getByRole('button', { name: /create an account instead/i }))
  await userEvent.type(screen.getByLabelText('Username'), 'asha')
  await userEvent.type(screen.getByLabelText('Password'), 'goa-trip-2026')
  await userEvent.type(screen.getByLabelText(/display name/i), 'Asha')
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
  expect(await screen.findByText('Plan page')).toBeInTheDocument()
  expect(String(fetch.mock.calls[0][0])).toMatch(/\/auth\/register$/)
})

test('shows a wrong-password message', async () => {
  mockApi([
    [
      /\/auth\/login$/,
      () =>
        new Response(
          JSON.stringify({ errorCode: 'BAD_CREDENTIALS', message: 'Wrong username or password' }),
          { status: 401 },
        ),
    ],
  ])
  renderLogin()
  await userEvent.type(screen.getByLabelText('Username'), 'asha')
  await userEvent.type(screen.getByLabelText('Password'), 'nope-nope-nope')
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Wrong username or password')
})
