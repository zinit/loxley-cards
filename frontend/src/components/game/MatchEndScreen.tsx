import type { RoundResultView } from '../../api/types'

interface MatchEndScreenProps {
  matchWinner: string | null
  roundHistory: RoundResultView[]
  onPlayAgain: () => void
  onBackToCampaign: () => void
}

function MatchEndScreen({
  matchWinner,
  roundHistory,
  onPlayAgain,
  onBackToCampaign,
}: MatchEndScreenProps) {
  const victory = matchWinner === 'P1'
  const draw = matchWinner === null

  const titleClass = victory
    ? 'match-end-victory'
    : draw
      ? 'match-end-draw'
      : 'match-end-defeat'

  const titleText = victory ? 'VICTORY' : draw ? 'DRAW' : 'DEFEAT'

  return (
    <div className="match-end-overlay">
      <div className="match-end-card">
        <div className={`match-end-title ${titleClass}`}>
          {titleText}
        </div>

        <div className="match-end-rounds">
          {roundHistory.map((r) => {
            const won = r.winner === 'P1'
            const roundDraw = r.winner === null
            return (
              <div key={r.roundNumber} className="match-end-round-row">
                <span>R{r.roundNumber}: {r.p1Score} – {r.p2Score}</span>
                <span className={won ? 'round-mark-win' : roundDraw ? 'round-mark-draw' : 'round-mark-loss'}>
                  {won ? '✓' : roundDraw ? '—' : '✗'}
                </span>
              </div>
            )
          })}
        </div>

        <div className="match-end-actions">
          <button type="button" className="action-btn action-btn-leader" onClick={onPlayAgain}>
            Play Again
          </button>
          <button type="button" className="action-btn action-btn-pass" onClick={onBackToCampaign}>
            Back to Campaign
          </button>
        </div>
      </div>
    </div>
  )
}

export default MatchEndScreen
