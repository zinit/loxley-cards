import type { CardInstanceView } from '../../api/types'
import Card from './Card'

const HAND_CARD_SIZE = 140

interface PlayerHandProps {
  hand: CardInstanceView[]
  selectedCardInstanceId: string | null
  interactionDisabled: boolean
  canPass: boolean
  canLeader: boolean
  onCardClick: (instanceId: string) => void
  onPass: () => void
  onLeader: () => void
}

function PlayerHand({
  hand,
  selectedCardInstanceId,
  interactionDisabled,
  canPass,
  canLeader,
  onCardClick,
  onPass,
  onLeader,
}: PlayerHandProps) {
  return (
    <>
      <div className={`board-hand ${interactionDisabled ? 'board-hand-disabled' : ''}`}>
        {hand.map((card) => (
          <Card
            key={card.instanceId}
            testId="hand-card"
            cardId={card.cardId}
            name={card.name}
            currentStrength={card.currentStrength}
            basePower={card.basePower}
            row={card.row}
            size={HAND_CARD_SIZE}
            selected={selectedCardInstanceId === card.instanceId}
            onClick={() => onCardClick(card.instanceId)}
          />
        ))}
      </div>

      <div className="game-action-buttons">
        {canPass && (
          <button type="button" className="action-btn action-btn-pass" onClick={onPass} data-testid="pass-button">
            Pass
          </button>
        )}
        {canLeader && (
          <button type="button" className="action-btn action-btn-leader" onClick={onLeader} data-testid="leader-button">
            Use Leader
          </button>
        )}
      </div>
    </>
  )
}

export default PlayerHand
