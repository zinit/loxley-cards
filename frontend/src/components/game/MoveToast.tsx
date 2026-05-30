import { useEffect, useRef } from 'react'
import type { LastMoveView } from '../../api/types'

interface MoveToastProps {
  yourLastMove: LastMoveView | null
  opponentLastMove: LastMoveView | null
  onDismiss: () => void
}

function formatMove(move: LastMoveView): string {
  const who = move.player === 'you' ? 'You' : 'Opponent'
  if (move.kind === 'PASS') return `${who} passed`
  if (move.kind === 'LEADER') return `${who} used leader ability`
  const card = move.cardName ?? 'a card'
  if (move.rowKind) return `${who} played ${card} on ${move.rowKind}`
  return `${who} played ${card}`
}

function MoveToast({ yourLastMove, opponentLastMove, onDismiss }: MoveToastProps) {
  const dismissRef = useRef(onDismiss)
  dismissRef.current = onDismiss
  useEffect(() => {
    const t = setTimeout(() => dismissRef.current(), 6000)
    return () => clearTimeout(t)
  }, [])

  const lines: string[] = []
  if (yourLastMove) lines.push(formatMove(yourLastMove))
  if (opponentLastMove) lines.push(formatMove(opponentLastMove))
  if (lines.length === 0) return null

  return (
    <div className="move-toast" onClick={onDismiss}>
      {lines.map((line, i) => (
        <div key={i} className="move-toast-line">{line}</div>
      ))}
    </div>
  )
}

export default MoveToast
