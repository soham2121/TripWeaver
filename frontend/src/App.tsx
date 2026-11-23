import { useEffect, useState } from 'react'
import { api } from './api/client'

type Health = { status: string }

export default function App() {
  const [status, setStatus] = useState<'loading' | 'up' | 'down'>('loading')

  useEffect(() => {
    api<Health>('/health')
      .then((h) => setStatus(h.status === 'UP' ? 'up' : 'down'))
      .catch(() => setStatus('down'))
  }, [])

  return (
    <main className="mx-auto max-w-3xl p-6">
      <h1 className="text-2xl font-semibold">TripWeaver</h1>
      <p className="mt-2 text-sm" role="status">
        API: {status === 'loading' ? 'checking…' : status === 'up' ? 'connected' : 'unavailable'}
      </p>
    </main>
  )
}
