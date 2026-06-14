import type { AuthUser } from './types'

async function parseError(res: Response): Promise<string> {
  try {
    const err = await res.json()
    return err.message || `HTTP ${res.status}`
  } catch {
    return res.statusText || `HTTP ${res.status}`
  }
}

export async function register(username: string, password: string): Promise<AuthUser> {
  const res = await fetch('/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}

export async function login(username: string, password: string): Promise<AuthUser> {
  const res = await fetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}

export async function logout(): Promise<void> {
  const res = await fetch('/auth/logout', { method: 'POST' })
  if (!res.ok) throw new Error(await parseError(res))
}

export async function fetchCurrentUser(): Promise<AuthUser | null> {
  const res = await fetch('/auth/me')
  if (res.status === 401) return null
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}
