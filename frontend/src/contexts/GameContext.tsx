import { createContext, useContext, type ReactNode, type Dispatch } from 'react'
import { useGameReducer, type GameUIState, type GameAction } from '../hooks/useGameReducer'

interface GameContextValue {
  state: GameUIState
  dispatch: Dispatch<GameAction>
}

const GameContext = createContext<GameContextValue | null>(null)

export function GameProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useGameReducer()
  return (
    <GameContext.Provider value={{ state, dispatch }}>
      {children}
    </GameContext.Provider>
  )
}

export function useGame(): GameContextValue {
  const ctx = useContext(GameContext)
  if (!ctx) {
    throw new Error('useGame must be used within a GameProvider')
  }
  return ctx
}
