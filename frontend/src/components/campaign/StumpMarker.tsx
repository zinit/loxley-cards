import stumpImage from '../../assets/markers/stump.webp';

function toRoman(num: number): string {
  if (num < 1 || num > 10) return String(num);
  const romans = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X'];
  return romans[num - 1];
}

interface StumpMarkerProps {
  number: number;
  size?: number;
  status: 'active' | 'locked' | 'completed';
  onClick?: () => void;
}

function StumpMarker({
  number,
  size = 100,
  status,
  onClick,
}: StumpMarkerProps) {
  return (
    <button
      type="button"
      onClick={status === 'locked' ? undefined : onClick}
      className={`stump-marker stump-${status}`}
      style={{
        width: `${size}px`,
        height: `${size}px`,
        fontSize: `${size}px`,
      }}
      disabled={status === 'locked'}
      aria-label={`Stage ${number}`}
      data-testid={`stage-${number}`}
      data-status={status}
    >
      {status === 'active' && (
        <span className="stump-sparkle-extra" aria-hidden="true" />
      )}
      <img
        src={stumpImage}
        alt=""
        className="stump-image"
        draggable={false}
      />
      <span className="stump-number">{toRoman(number)}</span>
    </button>
  );
}

export default StumpMarker;
