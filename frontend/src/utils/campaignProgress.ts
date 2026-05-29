const STORAGE_KEY = 'loxley_highest_unlocked'

export function getHighestUnlocked(): number {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === null) return 1
    const parsed = parseInt(stored, 10)
    return Number.isNaN(parsed) ? 1 : Math.max(1, parsed)
  } catch {
    return 1
  }
}

export function unlockStage(stageId: number): void {
  try {
    const current = getHighestUnlocked()
    if (stageId > current) {
      localStorage.setItem(STORAGE_KEY, String(stageId))
    }
  } catch {
    // localStorage unavailable — progress lost but app keeps running
  }
}
