import type { GameStateView, MoveRequest } from './types'

const API_BASE = import.meta.env.VITE_API_URL ?? ''

async function parseError(res: Response): Promise<string> {
  try {
    const err = await res.json()
    return err.message || `HTTP ${res.status}`
  } catch {
    return res.statusText || `HTTP ${res.status}`
  }
}

export async function createGame(stageNumber: number): Promise<GameStateView> {
  const res = await fetch(`${API_BASE}/api/games`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ stageNumber }),
  })
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}

export async function getGameState(gameId: string): Promise<GameStateView> {
  const res = await fetch(`${API_BASE}/api/games/${gameId}`, {
    credentials: 'include',
  })
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}

export async function makeMove(gameId: string, move: MoveRequest): Promise<GameStateView> {
  const res = await fetch(`${API_BASE}/api/games/${gameId}/moves`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(move),
  })
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}
