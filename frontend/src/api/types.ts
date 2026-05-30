export interface LastMoveView {
  player: string
  kind: string
  cardName: string | null
  rowKind: string | null
}

export interface GameStateView {
  gameId: string
  roundNumber: number
  currentTurn: string
  yourTurn: boolean
  matchEnded: boolean
  matchWinner: string | null
  you: PlayerView
  opponent: OpponentView
  roundHistory: RoundResultView[]
  legalMoves: MoveView[]
  yourLastMove: LastMoveView | null
  opponentLastMove: LastMoveView | null
}

export interface PlayerView {
  board: BoardSideView
  hand: CardInstanceView[]
  deckSize: number
  graveyardSize: number
  leaderUsed: boolean
  passed: boolean
  roundsWon: number
  totalStrength: number
}

export interface OpponentView {
  board: BoardSideView
  handSize: number
  deckSize: number
  graveyardSize: number
  leaderUsed: boolean
  passed: boolean
  roundsWon: number
  totalStrength: number
}

export interface BoardSideView {
  close: RowView
  ranged: RowView
  siege: RowView
  totalStrength: number
}

export interface RowView {
  units: CardInstanceView[]
  weatherActive: boolean
  hornActive: boolean
  strength: number
}

export interface CardInstanceView {
  instanceId: string
  cardId: string
  name: string
  cardType: string
  row: string | null
  basePower: number | null
  currentStrength: number
  abilities: string[]
  playTarget: string | null
}

export interface MoveView {
  kind: string
  handInstanceId: string | null
  cardName: string | null
  targetRow: string | null
  targetInstanceId: string | null
  description: string
}

export interface MoveRequest {
  kind: string
  handInstanceId?: string | null
  targetRow?: string | null
  targetInstanceId?: string | null
}

export interface RoundResultView {
  roundNumber: number
  p1Score: number
  p2Score: number
  winner: string | null
}

export interface ErrorResponse {
  code: string
  message: string
}
