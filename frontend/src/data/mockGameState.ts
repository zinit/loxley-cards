import { FINAL_DECK, type FinalCard } from './finalDeck';

export interface MockGameState {
  playerRows: {
    close: FinalCard[];
    ranged: FinalCard[];
    siege: FinalCard[];
  };
  opponentRows: {
    close: FinalCard[];
    ranged: FinalCard[];
    siege: FinalCard[];
  };
  playerHand: FinalCard[];
  opponentHandSize: number;
  playerDeckSize: number;
  playerDiscardSize: number;
  opponentDeckSize: number;
  opponentDiscardSize: number;
  playerScore: number;
  opponentScore: number;
  playerRoundsWon: number;
  opponentRoundsWon: number;
}

function pickRandom<T>(arr: T[], n: number): T[] {
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, n);
}

const boardUnits = FINAL_DECK.filter(
  (c) => c.power !== null && c.row !== null && (c.type === 'UNIT' || c.type === 'HERO'),
);

function buildMockState(): MockGameState {
  const drawn = pickRandom(boardUnits, 15);
  let i = 0;
  const take = (n: number) => drawn.slice(i, (i += n));

  return {
    playerRows: {
      close: take(2),
      ranged: take(1),
      siege: take(2),
    },
    opponentRows: {
      close: take(2),
      ranged: take(2),
      siege: take(1),
    },
    playerHand: take(5),
    opponentHandSize: 6,
    playerDeckSize: 14,
    playerDiscardSize: 2,
    opponentDeckSize: 12,
    opponentDiscardSize: 3,
    playerScore: 17,
    opponentScore: 24,
    playerRoundsWon: 1,
    opponentRoundsWon: 0,
  };
}

export const MOCK_GAME_STATE: MockGameState = buildMockState();
