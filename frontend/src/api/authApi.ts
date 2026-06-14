import type { AuthUser } from './types'

const API_BASE = import.meta.env.VITE_API_URL ?? ''

async function parseError(res: Response): Promise<string> {
  try {
    const err = await res.json()
    return err.message || `HTTP ${res.status}`
  } catch {
    return res.statusText || `HTTP ${res.status}`
  }
}

export async function register(username: string, password: string): Promise<AuthUser> {
  const res = await fetch(`${API_BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}

export async function login(username: string, password: string): Promise<AuthUser> {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}

export async function logout(): Promise<void> {
  const res = await fetch(`${API_BASE}/auth/logout`, {
    method: 'POST',
    credentials: 'include',
  })
  if (!res.ok) throw new Error(await parseError(res))
}

export async function fetchCurrentUser(): Promise<AuthUser | null> {
  const res = await fetch(`${API_BASE}/auth/me`, {
    credentials: 'include',
  })
  if (res.status === 401) return null
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}
