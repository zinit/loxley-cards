import { useReducer } from 'react'
import type { GameStateView, MoveView, RoundResultView } from '../api/types'

export type GamePhase =
  | 'idle'
  | 'loading'
  | 'selecting-target'
  | 'waiting-for-bot'
  | 'round-ended'
  | 'match-ended'

export type TargetingMode = 'row' | 'unit' | null

export interface GameUIState {
  gameState: GameStateView | null
  selectedCardInstanceId: string | null
  validTargets: MoveView[]
  targetingMode: TargetingMode
  phase: GamePhase
  error: string | null
  roundResult: RoundResultView | null
}

export type GameAction =
  | { type: 'GAME_LOADED'; payload: GameStateView }
  | { type: 'CARD_SELECTED'; payload: { instanceId: string; legalMoves: MoveView[] } }
  | { type: 'CARD_DESELECTED' }
  | { type: 'MOVE_SUBMITTING' }
  | { type: 'MOVE_RESULT'; payload: GameStateView }
  | { type: 'ROUND_DISMISSED' }
  | { type: 'RESTART_GAME' }
  | { type: 'ERROR'; payload: string }
  | { type: 'ERROR_DISMISSED' }

function determineTargetingMode(moves: MoveView[]): TargetingMode {
  if (moves.length === 0) return null
  if (moves.every(m => m.targetInstanceId != null)) return 'unit'
  if (moves.some(m => m.targetRow != null)) return 'row'
  return null
}

function detectRoundEnd(
  oldState: GameStateView | null,
  newState: GameStateView,
): RoundResultView | null {
  if (!oldState) return null
  const oldLen = oldState.roundHistory.length
  const newLen = newState.roundHistory.length
  if (newLen > oldLen) {
    return newState.roundHistory[newLen - 1]
  }
  return null
}

function gameReducer(state: GameUIState, action: GameAction): GameUIState {
  switch (action.type) {
    case 'GAME_LOADED':
      return {
        ...state,
        gameState: action.payload,
        phase: 'idle',
        error: null,
        selectedCardInstanceId: null,
        validTargets: [],
        targetingMode: null,
        roundResult: null,
      }

    case 'CARD_SELECTED': {
      const moves = action.payload.legalMoves
      const mode = determineTargetingMode(moves)
      return {
        ...state,
        selectedCardInstanceId: action.payload.instanceId,
        validTargets: moves,
        targetingMode: mode,
        phase: mode ? 'selecting-target' : state.phase,
      }
    }

    case 'CARD_DESELECTED':
      return {
        ...state,
        selectedCardInstanceId: null,
        validTargets: [],
        targetingMode: null,
        phase: 'idle',
      }

    case 'MOVE_SUBMITTING':
      return {
        ...state,
        selectedCardInstanceId: null,
        validTargets: [],
        targetingMode: null,
        phase: 'waiting-for-bot',
      }

    case 'MOVE_RESULT': {
      const roundResult = detectRoundEnd(state.gameState, action.payload)
      let nextPhase: GamePhase = 'idle'
      if (action.payload.matchEnded) {
        nextPhase = 'match-ended'
      } else if (roundResult) {
        nextPhase = 'round-ended'
      }
      return {
        ...state,
        gameState: action.payload,
        phase: nextPhase,
        roundResult,
        error: null,
      }
    }

    case 'ROUND_DISMISSED':
      return {
        ...state,
        phase: 'idle',
        roundResult: null,
      }

    case 'RESTART_GAME':
      return {
        ...state,
        gameState: null,
        selectedCardInstanceId: null,
        validTargets: [],
        targetingMode: null,
        phase: 'loading',
        error: null,
        roundResult: null,
      }

    case 'ERROR':
      return {
        ...state,
        error: action.payload,
        phase: 'idle',
        selectedCardInstanceId: null,
        validTargets: [],
        targetingMode: null,
      }

    case 'ERROR_DISMISSED':
      return {
        ...state,
        error: null,
      }

    default:
      return state
  }
}

const initialState: GameUIState = {
  gameState: null,
  selectedCardInstanceId: null,
  validTargets: [],
  targetingMode: null,
  phase: 'loading',
  error: null,
  roundResult: null,
}

export function useGameReducer() {
  return useReducer(gameReducer, initialState)
}
