import { useEffect } from 'react'
import type { RoundResultView } from '../../api/types'

interface RoundOverlayProps {
  roundResult: RoundResultView
  onDismiss: () => void
}

function RoundOverlay({ roundResult, onDismiss }: RoundOverlayProps) {
  useEffect(() => {
    const t = setTimeout(onDismiss, 3000)
    return () => clearTimeout(t)
  }, [onDismiss])

  const playerWon = roundResult.winner === 'P1'
  const draw = roundResult.winner === null
  const label = draw ? 'Draw!' : playerWon ? 'Victory!' : 'Defeat'

  return (
    <div className="round-overlay" onClick={onDismiss}>
      <div className="round-overlay-banner">
        <div className="round-overlay-title">
          Round {roundResult.roundNumber} — {label}
        </div>
        <div className="round-overlay-scores">
          {roundResult.p1Score} vs {roundResult.p2Score}
        </div>
      </div>
    </div>
  )
}

export default RoundOverlay
