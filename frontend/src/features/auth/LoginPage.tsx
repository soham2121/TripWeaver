import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { authApi } from '../../api/trips'
import { useAuth } from './auth'

/** Sign in or create an account (username + password). */
export function LoginPage() {
  const [params] = useSearchParams()
  const next = params.get('next')?.startsWith('/') ? params.get('next')! : '/trips'
  const navigate = useNavigate()
  const { setUser } = useAuth()
  const [register, setRegister] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    setBusy(true)
    setError('')
    try {
      const u = register
        ? await authApi.register(username, password, displayName || undefined)
        : await authApi.login(username, password)
      setUser(u)
      navigate(next)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not reach the server.')
    } finally {
      setBusy(false)
    }
  }

  const input = 'field'
  return (
    <form
      className="mx-auto max-w-sm space-y-3 card p-4 text-sm"
      onSubmit={(e) => {
        e.preventDefault()
        void submit()
      }}
    >
      <h1 className="text-xl font-bold">{register ? 'Create an account' : 'Sign in'}</h1>
      <p className="text-xs text-zinc-500">
        An account lets you save trips, share them and plan with friends.
      </p>
      <label className="block">
        Username
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          className={input}
        />
      </label>
      <label className="block">
        Password
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete={register ? 'new-password' : 'current-password'}
          className={input}
        />
      </label>
      {register && (
        <>
          <p className="text-xs text-zinc-500">At least 8 characters.</p>
          <label className="block">
            Display name (optional)
            <input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className={input}
            />
          </label>
        </>
      )}
      {error && (
        <p role="alert" className="text-amber-800">
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={busy || !username || !password}
        className="w-full btn-primary"
      >
        {register ? 'Create account' : 'Sign in'}
      </button>
      <button
        type="button"
        onClick={() => setRegister(!register)}
        className="w-full text-teal-700 underline"
      >
        {register ? 'I already have an account' : 'Create an account instead'}
      </button>
    </form>
  )
}
