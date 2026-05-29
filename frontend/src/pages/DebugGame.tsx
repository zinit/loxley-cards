import { useState } from 'react'
import { createGame, makeMove } from '../api/gameApi'
import type { GameStateView, MoveView, MoveRequest, RowView } from '../api/types'

export default function DebugGame() {
  const [state, setState] = useState<GameStateView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [stage, setStage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [jsonOpen, setJsonOpen] = useState(false)

  async function handleNewGame() {
    setError(null)
    setLoading(true)
    try {
      const gs = await createGame(stage)
      setState(gs)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }

  async function handleMove(move: MoveView) {
    if (!state) return
    setError(null)
    setLoading(true)
    const req: MoveRequest = {
      kind: move.kind,
      handInstanceId: move.handInstanceId,
      targetRow: move.targetRow,
      targetInstanceId: move.targetInstanceId,
    }
    try {
      const gs = await makeMove(state.gameId, req)
      setState(gs)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }

  function renderRow(label: string, row: RowView) {
    const flags = [
      row.weatherActive && 'WEATHER',
      row.hornActive && 'HORN',
    ].filter(Boolean).join(', ')
    return (
      <div key={label} style={{ marginBottom: 4 }}>
        <strong>{label}</strong> (str: {row.strength}{flags ? `, ${flags}` : ''}):
        {row.units.length === 0
          ? ' (empty)'
          : row.units.map(u => ` ${u.name}[${u.currentStrength}]`).join(',')
        }
      </div>
    )
  }

  return (
    <div style={{ position: 'fixed', inset: 0, overflow: 'auto', background: '#fff', color: '#000', zIndex: 200 }}>
      <div style={{ fontFamily: 'monospace', padding: 16, maxWidth: 900, margin: '0 auto' }}>
      <h2>Debug Game — S-02 API Contract Validator</h2>

      {/* Stage selector */}
      <div style={{ marginBottom: 16 }}>
        <label>Stage: </label>
        <select value={stage} onChange={e => setStage(Number(e.target.value))}>
          {Array.from({ length: 10 }, (_, i) => i + 1).map(n => (
            <option key={n} value={n}>{n}</option>
          ))}
        </select>
        {' '}
        <button onClick={handleNewGame} disabled={loading}>New Game</button>
      </div>

      {/* Error banner */}
      {error && (
        <div style={{ background: '#fee', border: '1px solid #c00', padding: 8, marginBottom: 12 }}>
          Error: {error}
        </div>
      )}

      {!state && <div>Select a stage and click "New Game" to start.</div>}

      {state && (
        <>
          {/* Header */}
          <div style={{ marginBottom: 12, padding: 8, background: '#eee' }}>
            <strong>Round {state.roundNumber}</strong>
            {' | '}Turn: {state.currentTurn} ({state.yourTurn ? 'YOUR TURN' : 'waiting'})
            {state.matchEnded && (
              <span style={{ color: '#c00', fontWeight: 'bold' }}>
                {' | '}MATCH ENDED — Winner: {state.matchWinner ?? 'DRAW'}
              </span>
            )}
            {' | '}Score: You {state.you.roundsWon} - {state.opponent.roundsWon} Opp
          </div>

          {/* Round history */}
          {state.roundHistory.length > 0 && (
            <div style={{ marginBottom: 12 }}>
              <strong>Round History:</strong>
              {state.roundHistory.map(r => (
                <div key={r.roundNumber}>
                  R{r.roundNumber}: P1={r.p1Score} P2={r.p2Score} → {r.winner ?? 'DRAW'}
                </div>
              ))}
            </div>
          )}

          {/* Your hand */}
          <div style={{ marginBottom: 12 }}>
            <strong>Your Hand ({state.you.hand.length} cards):</strong>
            <ul style={{ margin: '4px 0', paddingLeft: 20 }}>
              {state.you.hand.map(c => (
                <li key={c.instanceId}>
                  {c.name} [{c.cardType}] {c.basePower != null ? `pow:${c.basePower}` : ''} {c.abilities.length > 0 ? `(${c.abilities.join(',')})` : ''}
                </li>
              ))}
            </ul>
          </div>

          {/* Board — text only */}
          <div style={{ marginBottom: 12 }}>
            <strong>Your Board</strong> (total: {state.you.totalStrength})
            {' | '}Deck: {state.you.deckSize}, Grave: {state.you.graveyardSize}
            {state.you.leaderUsed ? ', Leader: USED' : ', Leader: available'}
            {state.you.passed && ' | PASSED'}
            {renderRow('  CLOSE', state.you.board.close)}
            {renderRow('  RANGED', state.you.board.ranged)}
            {renderRow('  SIEGE', state.you.board.siege)}
          </div>

          <div style={{ marginBottom: 12 }}>
            <strong>Opponent Board</strong> (total: {state.opponent.totalStrength})
            {' | '}Hand: {state.opponent.handSize}, Deck: {state.opponent.deckSize}, Grave: {state.opponent.graveyardSize}
            {state.opponent.leaderUsed ? ', Leader: USED' : ', Leader: available'}
            {state.opponent.passed && ' | PASSED'}
            {renderRow('  CLOSE', state.opponent.board.close)}
            {renderRow('  RANGED', state.opponent.board.ranged)}
            {renderRow('  SIEGE', state.opponent.board.siege)}
          </div>

          {/* Legal moves */}
          <div style={{ marginBottom: 12 }}>
            <strong>Legal Moves ({state.legalMoves.length}):</strong>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 4 }}>
              {state.legalMoves.map((m, i) => (
                <button
                  key={i}
                  onClick={() => handleMove(m)}
                  disabled={loading || state.matchEnded}
                  style={{ fontSize: 12, padding: '4px 8px' }}
                >
                  {m.description}
                </button>
              ))}
            </div>
          </div>

          {/* JSON dump */}
          <div>
            <button onClick={() => setJsonOpen(!jsonOpen)}>
              {jsonOpen ? 'Hide' : 'Show'} JSON dump
            </button>
            {jsonOpen && (
              <pre style={{ background: '#f5f5f5', padding: 8, overflow: 'auto', maxHeight: 400, fontSize: 11 }}>
                {JSON.stringify(state, null, 2)}
              </pre>
            )}
          </div>
        </>
      )}
      </div>
    </div>
  )
}
