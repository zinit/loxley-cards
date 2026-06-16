import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { AuthUser } from '../api/types'
import * as authApi from '../api/authApi'

interface AuthContextValue {
  user: AuthUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  updateHighestUnlockedStage: (stage: number) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    authApi.fetchCurrentUser()
      .then((u) => setUser(u))
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const u = await authApi.login(username, password)
    setUser(u)
  }, [])

  const register = useCallback(async (username: string, password: string) => {
    const u = await authApi.register(username, password)
    setUser(u)
  }, [])

  const logout = useCallback(async () => {
    await authApi.logout()
    setUser(null)
  }, [])

  const updateHighestUnlockedStage = useCallback((stage: number) => {
    setUser((prev) => prev ? { ...prev, highestUnlockedStage: stage } : prev)
  }, [])

  const value = useMemo(
    () => ({ user, loading, login, register, logout, updateHighestUnlockedStage }),
    [user, loading, login, register, logout, updateHighestUnlockedStage],
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
