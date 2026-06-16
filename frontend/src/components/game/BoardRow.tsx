import type { RowView } from '../../api/types'
import Card from './Card'

const CARD_SIZE = 110

interface BoardRowProps {
  row: RowView
  rowId: string
  side: 'player' | 'opponent'
  isValidTarget: boolean
  onRowClick: () => void
  validTargetUnitIds: string[]
  onUnitClick: (instanceId: string) => void
}

function BoardRow({
  row,
  rowId,
  side,
  isValidTarget,
  onRowClick,
  validTargetUnitIds,
  onUnitClick,
}: BoardRowProps) {
  const rowClasses = [
    'board-row',
    `board-row-${rowId.toLowerCase()}`,
    `board-row-${side}`,
    isValidTarget && 'board-row-valid-target',
    row.weatherActive && 'board-row-weather',
    row.hornActive && 'board-row-horn',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div
      className={rowClasses}
      onClick={() => isValidTarget && onRowClick()}
      data-testid={`target-row-${rowId}`}
    >
      <div className="row-label">{rowId}</div>
      <div className="row-cards">
        {row.units.map((unit) => {
          const unitIsTarget = validTargetUnitIds.includes(unit.instanceId)
          return (
            <Card
              key={unit.instanceId}
              cardId={unit.cardId}
              name={unit.name}
              currentStrength={unit.currentStrength}
              basePower={unit.basePower}
              row={unit.row}
              size={CARD_SIZE}
              isValidTarget={unitIsTarget}
              onClick={unitIsTarget ? () => onUnitClick(unit.instanceId) : undefined}
            />
          )
        })}
      </div>
      <div className="row-score">{row.strength}</div>
    </div>
  )
}

export default BoardRow
