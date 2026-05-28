import type { CardRow } from '../../data/finalDeck';
import robinImage from '../../assets/cards/robin-placeholder.webp';
import iconClose from '../../assets/cards/icons/close.webp';
import iconRanged from '../../assets/cards/icons/ranged.webp';
import iconSiege from '../../assets/cards/icons/siege.webp';

interface CardProps {
  name: string;
  power: number | null;
  row: CardRow | null;
  image?: string;
  ability?: string | null;
  size?: number;
  onClick?: () => void;
  selected?: boolean;
}

const ROW_ICONS: Record<CardRow, string> = {
  CLOSE: iconClose,
  RANGED: iconRanged,
  SIEGE: iconSiege,
};

function Card({
  name,
  power,
  row,
  image,
  size = 200,
  onClick,
  selected = false,
}: CardProps) {
  const width = size * 0.714;

  return (
    <div
      className={`game-card ${selected ? 'game-card-selected' : ''}`}
      style={{ width: `${width}px`, height: `${size}px` }}
      onClick={onClick}
    >
      <img
        src={image ?? robinImage}
        alt={name}
        className="game-card-portrait"
        draggable={false}
      />

      <div className="game-card-frame" />

      {power !== null && <div className="game-card-power">{power}</div>}

      {row !== null && (
        <img
          src={ROW_ICONS[row]}
          alt={row}
          className="game-card-row-icon"
          draggable={false}
        />
      )}

      <div className="game-card-name">{name}</div>
    </div>
  );
}

export default Card;
