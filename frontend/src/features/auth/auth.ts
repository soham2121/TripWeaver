import { createContext, useContext } from 'react'
import type { AppUser } from '../../types/trip'

export interface Auth {
  user: AppUser | null
  /** True until the first /auth/me answer. */
  loading: boolean
  setUser: (u: AppUser | null) => void
}

export const AuthContext = createContext<Auth>({ user: null, loading: false, setUser: () => {} })

export const useAuth = () => useContext(AuthContext)
