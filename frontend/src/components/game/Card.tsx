import { getCardImage } from '../../data/cardImageMap'
import iconClose from '../../assets/cards/icons/close.webp'
import iconRanged from '../../assets/cards/icons/ranged.webp'
import iconSiege from '../../assets/cards/icons/siege.webp'

interface CardProps {
  cardId: string
  name: string
  currentStrength: number
  basePower: number | null
  row: string | null
  size?: number
  onClick?: () => void
  selected?: boolean
  isValidTarget?: boolean
  testId?: string
}

const ROW_ICONS: Record<string, string> = {
  CLOSE: iconClose,
  RANGED: iconRanged,
  SIEGE: iconSiege,
}

function Card({
  cardId,
  name,
  currentStrength,
  basePower,
  row,
  size = 200,
  onClick,
  selected = false,
  isValidTarget = false,
  testId,
}: CardProps) {
  const width = size * 0.714
  const image = getCardImage(cardId)

  let powerClass = 'game-card-power'
  if (basePower != null && currentStrength < basePower) {
    powerClass += ' power-reduced'
  } else if (basePower != null && currentStrength > basePower) {
    powerClass += ' power-boosted'
  }

  const cardClasses = [
    'game-card',
    selected && 'game-card-selected',
    isValidTarget && 'board-card-valid-target',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div
      className={cardClasses}
      style={{ width: `${width}px`, height: `${size}px` }}
      onClick={onClick}
      data-testid={testId}
    >
      <img
        src={image}
        alt={name}
        className="game-card-portrait"
        draggable={false}
      />

      <div className="game-card-frame" />

      {basePower !== null && (
        <div className={powerClass}>{currentStrength}</div>
      )}

      {row !== null && ROW_ICONS[row] && (
        <img
          src={ROW_ICONS[row]}
          alt={row}
          className="game-card-row-icon"
          draggable={false}
        />
      )}

      <div className="game-card-name">{name}</div>
    </div>
  )
}

export default Card
