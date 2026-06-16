import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { GameProvider, useGame } from '../contexts/GameContext'
import { createGame, getGameState, makeMove } from '../api/gameApi'
import type { MoveRequest } from '../api/types'
import BoardRow from '../components/game/BoardRow'
import MetaPanel from '../components/game/MetaPanel'
import PlayerHand from '../components/game/PlayerHand'
import RoundOverlay from '../components/game/RoundOverlay'
import MatchEndScreen from '../components/game/MatchEndScreen'
import MoveToast from '../components/game/MoveToast'
import { useAuth } from '../contexts/AuthContext'
import tableStumpImage from '../assets/markers/table-stump.webp'

function GameBoardInner() {
  const { stageId } = useParams<{ stageId: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { state, dispatch } = useGame()
  const { updateHighestUnlockedStage } = useAuth()
  const { gameState, selectedCardInstanceId, validTargets, targetingMode, phase, error } = state

  const [stumpIn, setStumpIn] = useState(false)
  const [boardIn, setBoardIn] = useState(false)
  const exitTimers = useRef<ReturnType<typeof setTimeout>[]>([])
  const gameIdRef = useRef<string | null>(null)
  const botTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const submittingRef = useRef(false)

  // Entrance animation + game creation
  useEffect(() => {
    const t1 = setTimeout(() => setStumpIn(true), 16)
    const t2 = setTimeout(() => setBoardIn(true), 700)

    const existingGameId = searchParams.get('gameId')
    const loadGame = existingGameId
      ? getGameState(existingGameId)
      : createGame(parseInt(stageId ?? '1', 10))

    loadGame
      .then((gs) => {
        gameIdRef.current = gs.gameId
        dispatch({ type: 'GAME_LOADED', payload: gs })
      })
      .catch((err) => {
        dispatch({ type: 'ERROR', payload: err.message ?? 'Failed to create game' })
      })

    return () => {
      clearTimeout(t1)
      clearTimeout(t2)
      exitTimers.current.forEach(clearTimeout)
      if (botTimerRef.current) clearTimeout(botTimerRef.current)
    }
  }, [stageId, searchParams, dispatch])

  // Unlock next stage on match victory
  useEffect(() => {
    if (phase !== 'match-ended' || !gameState?.matchEnded) return
    if (gameState.matchWinner === 'P1' && gameState.newHighestUnlockedStage != null) {
      updateHighestUnlockedStage(gameState.newHighestUnlockedStage)
    }
  }, [phase, gameState, updateHighestUnlockedStage])

  const handleBack = useCallback(() => {
    exitTimers.current.forEach(clearTimeout)
    setBoardIn(false)
    exitTimers.current = [
      setTimeout(() => setStumpIn(false), 250),
      setTimeout(() => navigate('/'), 850),
    ]
  }, [navigate])

  // Play Again handler
  const handlePlayAgain = useCallback(() => {
    dispatch({ type: 'RESTART_GAME' })
    gameIdRef.current = null
    const stageNumber = parseInt(stageId ?? '1', 10)
    createGame(stageNumber)
      .then((gs) => {
        gameIdRef.current = gs.gameId
        dispatch({ type: 'GAME_LOADED', payload: gs })
      })
      .catch((err) => {
        dispatch({ type: 'ERROR', payload: err.message ?? 'Failed to create game' })
      })
  }, [stageId, dispatch])

  // Submit a move to the API
  const submitMove = useCallback(
    async (moveReq: MoveRequest) => {
      if (!gameIdRef.current || submittingRef.current) return
      submittingRef.current = true
      dispatch({ type: 'MOVE_SUBMITTING' })
      try {
        const result = await makeMove(gameIdRef.current, moveReq)
        botTimerRef.current = setTimeout(() => {
          botTimerRef.current = null
          submittingRef.current = false
          dispatch({ type: 'MOVE_RESULT', payload: result })
        }, 600)
      } catch (err: unknown) {
        submittingRef.current = false
        const msg = err instanceof Error ? err.message : 'Move failed'
        dispatch({ type: 'ERROR', payload: msg })
      }
    },
    [dispatch],
  )

  // Card selection handler
  const handleCardClick = useCallback(
    (instanceId: string) => {
      if (!gameState || !gameState.yourTurn) return
      if (phase === 'waiting-for-bot' || phase === 'round-ended' || phase === 'match-ended') return

      if (selectedCardInstanceId === instanceId) {
        dispatch({ type: 'CARD_DESELECTED' })
        return
      }

      const movesForCard = gameState.legalMoves.filter(
        (m) => m.handInstanceId === instanceId,
      )
      if (movesForCard.length === 0) return

      if (movesForCard.every((m) => m.kind === 'SPECIAL')) {
        const move = movesForCard[0]
        submitMove({ kind: move.kind, handInstanceId: move.handInstanceId })
        return
      }

      dispatch({
        type: 'CARD_SELECTED',
        payload: { instanceId, legalMoves: movesForCard },
      })
    },
    [gameState, phase, selectedCardInstanceId, dispatch, submitMove],
  )

  // Row click handler
  const handleRowClick = useCallback(
    (rowId: string, side: 'player' | 'opponent') => {
      if (!selectedCardInstanceId || targetingMode !== 'row') return
      const matchingMove = validTargets.find((m) => {
        if (m.targetRow !== rowId) return false
        if (m.kind === 'SPY' && side !== 'opponent') return false
        if (m.kind === 'UNIT' && side !== 'player') return false
        if (m.kind === 'ROW' && side !== 'player') return false
        return true
      })
      if (!matchingMove) return
      submitMove({
        kind: matchingMove.kind,
        handInstanceId: matchingMove.handInstanceId,
        targetRow: matchingMove.targetRow,
        targetInstanceId: matchingMove.targetInstanceId,
      })
    },
    [selectedCardInstanceId, targetingMode, validTargets, submitMove],
  )

  // Unit click handler (decoy targeting)
  const handleUnitClick = useCallback(
    (instanceId: string) => {
      if (!selectedCardInstanceId || targetingMode !== 'unit') return
      const matchingMove = validTargets.find((m) => m.targetInstanceId === instanceId)
      if (!matchingMove) return
      submitMove({
        kind: matchingMove.kind,
        handInstanceId: matchingMove.handInstanceId,
        targetRow: matchingMove.targetRow,
        targetInstanceId: matchingMove.targetInstanceId,
      })
    },
    [selectedCardInstanceId, targetingMode, validTargets, submitMove],
  )

  // PASS / LEADER handlers
  const handlePass = useCallback(() => {
    if (!gameState?.yourTurn) return
    const passMove = gameState.legalMoves.find((m) => m.kind === 'PASS')
    if (passMove) submitMove({ kind: 'PASS' })
  }, [gameState, submitMove])

  const handleLeader = useCallback(() => {
    if (!gameState?.yourTurn) return
    const leaderMove = gameState.legalMoves.find((m) => m.kind === 'LEADER')
    if (leaderMove) submitMove({ kind: 'LEADER' })
  }, [gameState, submitMove])

  // Targeting helpers
  const isRowValidTarget = (rowId: string, side: 'player' | 'opponent'): boolean => {
    if (targetingMode !== 'row') return false
    return validTargets.some((m) => {
      if (m.targetRow !== rowId) return false
      if (m.kind === 'SPY') return side === 'opponent'
      return side === 'player'
    })
  }

  const validTargetUnitIds = targetingMode === 'unit'
    ? validTargets.map((m) => m.targetInstanceId).filter((id): id is string => id != null)
    : []

  // ESC to cancel selection
  useEffect(() => {
    if (!selectedCardInstanceId) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') dispatch({ type: 'CARD_DESELECTED' })
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [selectedCardInstanceId, dispatch])

  // Board background click to cancel
  const handleBoardClick = useCallback(
    (e: React.MouseEvent) => {
      if (!selectedCardInstanceId) return
      const target = e.target as HTMLElement
      if (target.closest('.game-card') || target.closest('.board-row-valid-target')) return
      dispatch({ type: 'CARD_DESELECTED' })
    },
    [selectedCardInstanceId, dispatch],
  )

  const interactionDisabled =
    !gameState?.yourTurn ||
    phase === 'waiting-for-bot' ||
    phase === 'loading' ||
    phase === 'round-ended' ||
    phase === 'match-ended'

  const canPass = gameState?.legalMoves.some((m) => m.kind === 'PASS') && !interactionDisabled
  const canLeader =
    gameState?.legalMoves.some((m) => m.kind === 'LEADER') &&
    !gameState?.you.leaderUsed &&
    !interactionDisabled

  const pageClasses = [
    'game-board-page',
    stumpIn && 'stump-in',
    boardIn && 'board-in',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={pageClasses}>
      <button
        type="button"
        className="game-table-back-button"
        onClick={handleBack}
        aria-label="Back to map"
      >
        ← Back to map
      </button>

      <div className="game-table-stump">
        <img
          src={tableStumpImage}
          alt=""
          className="game-table-stump-image"
          draggable={false}
        />
      </div>

      {error && (
        <div className="game-error-banner">
          <span>{error}</span>
          <button type="button" onClick={() => dispatch({ type: 'ERROR_DISMISSED' })}>
            Dismiss
          </button>
        </div>
      )}

      {phase === 'round-ended' && state.roundResult && (
        <RoundOverlay
          roundResult={state.roundResult}
          onDismiss={() => dispatch({ type: 'ROUND_DISMISSED' })}
        />
      )}

      {phase === 'match-ended' && gameState && (
        <MatchEndScreen
          matchWinner={gameState.matchWinner}
          roundHistory={gameState.roundHistory}
          onPlayAgain={handlePlayAgain}
          onBackToCampaign={() => navigate('/')}
        />
      )}

      {phase === 'idle' && (state.yourLastMove || state.opponentLastMove) && (
        <MoveToast
          yourLastMove={state.yourLastMove}
          opponentLastMove={state.opponentLastMove}
          onDismiss={() => dispatch({ type: 'TOAST_DISMISSED' })}
        />
      )}

      <div className="game-board" onClick={handleBoardClick}>
        {gameState ? (
          <>
            <div className="board-side board-side-opponent">
              <MetaPanel
                side="opponent"

                roundsWon={gameState.opponent.roundsWon}
                totalStrength={gameState.opponent.totalStrength}
                handCount={gameState.opponent.handSize}
                deckSize={gameState.opponent.deckSize}
                graveyardSize={gameState.opponent.graveyardSize}
                showBotThinking={phase === 'waiting-for-bot'}
                passed={gameState.opponent.passed}
              />
              <BoardRow row={gameState.opponent.board.siege} rowId="SIEGE" side="opponent" isValidTarget={isRowValidTarget('SIEGE', 'opponent')} onRowClick={() => handleRowClick('SIEGE', 'opponent')} validTargetUnitIds={validTargetUnitIds} onUnitClick={handleUnitClick} />
              <BoardRow row={gameState.opponent.board.ranged} rowId="RANGED" side="opponent" isValidTarget={isRowValidTarget('RANGED', 'opponent')} onRowClick={() => handleRowClick('RANGED', 'opponent')} validTargetUnitIds={validTargetUnitIds} onUnitClick={handleUnitClick} />
              <BoardRow row={gameState.opponent.board.close} rowId="CLOSE" side="opponent" isValidTarget={isRowValidTarget('CLOSE', 'opponent')} onRowClick={() => handleRowClick('CLOSE', 'opponent')} validTargetUnitIds={validTargetUnitIds} onUnitClick={handleUnitClick} />
            </div>

            <div className="board-divider" />

            <div className="board-side board-side-player">
              <BoardRow row={gameState.you.board.close} rowId="CLOSE" side="player" isValidTarget={isRowValidTarget('CLOSE', 'player')} onRowClick={() => handleRowClick('CLOSE', 'player')} validTargetUnitIds={validTargetUnitIds} onUnitClick={handleUnitClick} />
              <BoardRow row={gameState.you.board.ranged} rowId="RANGED" side="player" isValidTarget={isRowValidTarget('RANGED', 'player')} onRowClick={() => handleRowClick('RANGED', 'player')} validTargetUnitIds={validTargetUnitIds} onUnitClick={handleUnitClick} />
              <BoardRow row={gameState.you.board.siege} rowId="SIEGE" side="player" isValidTarget={isRowValidTarget('SIEGE', 'player')} onRowClick={() => handleRowClick('SIEGE', 'player')} validTargetUnitIds={validTargetUnitIds} onUnitClick={handleUnitClick} />

              <MetaPanel
                side="player"

                roundsWon={gameState.you.roundsWon}
                totalStrength={gameState.you.totalStrength}
                handCount={gameState.you.hand.length}
                deckSize={gameState.you.deckSize}
                graveyardSize={gameState.you.graveyardSize}
              />
            </div>

            <PlayerHand
              hand={gameState.you.hand}
              selectedCardInstanceId={selectedCardInstanceId}
              interactionDisabled={interactionDisabled}
              canPass={!!canPass}
              canLeader={!!canLeader}
              onCardClick={handleCardClick}
              onPass={handlePass}
              onLeader={handleLeader}
            />
          </>
        ) : (
          phase === 'loading' && (
            <div className="game-loading">Loading game...</div>
          )
        )}
      </div>
    </div>
  )
}

function GameBoard() {
  return (
    <GameProvider>
      <GameBoardInner />
    </GameProvider>
  )
}

export default GameBoard
