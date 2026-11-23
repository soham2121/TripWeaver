import { useEffect, useState, type ReactNode } from 'react'
import { authApi } from '../../api/trips'
import type { AppUser } from '../../types/trip'
import { AuthContext } from './auth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AppUser | null>(null)
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    authApi
      .me()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
  }, [])
  return <AuthContext value={{ user, loading, setUser }}>{children}</AuthContext>
}
