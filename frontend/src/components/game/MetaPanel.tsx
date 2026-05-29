interface MetaPanelProps {
  side: 'player' | 'opponent'
  roundsWon: number
  totalStrength: number
  handCount: number
  deckSize: number
  graveyardSize: number
  showBotThinking?: boolean
}

function MetaPanel({
  side,
  roundsWon,
  totalStrength,
  handCount,
  deckSize,
  graveyardSize,
  showBotThinking = false,
}: MetaPanelProps) {
  return (
    <div className={`board-meta board-meta-${side}`}>
      <div className="board-meta-left">
        <div className="board-leader-slot">
          <div className="board-leader-placeholder">
            <div className="leader-label">LEADER</div>
            <div className="leader-name">
              {side === 'player' ? "Robin's Cunning" : 'Opponent'}
            </div>
          </div>
        </div>

        <div className="board-score">
          <div className="score-rounds">
            {Array.from({ length: 2 }).map((_, i) => (
              <div
                key={i}
                className={`round-pip ${i < roundsWon ? 'round-pip-won' : ''}`}
              />
            ))}
          </div>
          <div className="score-total">{totalStrength}</div>
        </div>
      </div>

      <div className="board-meta-right">
        {showBotThinking && (
          <div className="bot-thinking">Bot is thinking...</div>
        )}
        <div className="board-hand-info">
          <div className="hand-count">{handCount}</div>
          <div className="hand-label">cards in hand</div>
        </div>

        <div className="board-deck-stack">
          <div className="deck-pile">
            <div className="deck-count">{deckSize}</div>
            <div className="deck-label">DECK</div>
          </div>
          <div className="deck-pile">
            <div className="deck-count">{graveyardSize}</div>
            <div className="deck-label">GRAVE</div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default MetaPanel
