import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { MOCK_GAME_STATE } from '../data/mockGameState';
import type { FinalCard } from '../data/finalDeck';
import Card from '../components/game/Card';
import tableStumpImage from '../assets/markers/table-stump.webp';

const CARD_SIZE = 110;
const HAND_CARD_SIZE = 140;

function sumPower(cards: FinalCard[]): number {
  return cards.reduce((sum, c) => sum + (c.power ?? 0), 0);
}

function GameBoard() {
  const state = MOCK_GAME_STATE;
  const navigate = useNavigate();
  const [stumpIn, setStumpIn] = useState(false);
  const [boardIn, setBoardIn] = useState(false);
  const exitTimers = useRef<ReturnType<typeof setTimeout>[]>([]);

  useEffect(() => {
    const t1 = setTimeout(() => setStumpIn(true), 16);
    const t2 = setTimeout(() => setBoardIn(true), 700);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      exitTimers.current.forEach(clearTimeout);
    };
  }, []);

  const handleBack = () => {
    setBoardIn(false);
    exitTimers.current = [
      setTimeout(() => setStumpIn(false), 250),
      setTimeout(() => navigate('/'), 850),
    ];
  };

  const pageClasses = [
    'game-board-page',
    stumpIn && 'stump-in',
    boardIn && 'board-in',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={pageClasses}>
      <button
        type="button"
        className="game-table-back-button"
        onClick={handleBack}
        aria-label="Back to map"
      >
        ← Back to map
      </button>

      <div className="game-table-stump">
        <img
          src={tableStumpImage}
          alt=""
          className="game-table-stump-image"
          draggable={false}
        />
      </div>

      <div className="game-board">
        <div className="board-side board-side-opponent">
          <div className="board-meta board-meta-opponent">
            <div className="board-meta-left">
              <div className="board-leader-slot">
                <div className="board-leader-placeholder">
                  <div className="leader-label">LEADER</div>
                  <div className="leader-name">Sir Guy</div>
                </div>
              </div>

              <div className="board-score">
                <div className="score-rounds">
                  {Array.from({ length: 2 }).map((_, i) => (
                    <div
                      key={i}
                      className={`round-pip ${i < state.opponentRoundsWon ? 'round-pip-won' : ''}`}
                    />
                  ))}
                </div>
                <div className="score-total">{state.opponentScore}</div>
              </div>
            </div>

            <div className="board-meta-right">
              <div className="board-hand-info">
                <div className="hand-count">{state.opponentHandSize}</div>
                <div className="hand-label">cards in hand</div>
              </div>

              <div className="board-deck-stack">
                <div className="deck-pile">
                  <div className="deck-count">{state.opponentDeckSize}</div>
                  <div className="deck-label">DECK</div>
                </div>
                <div className="deck-pile">
                  <div className="deck-count">{state.opponentDiscardSize}</div>
                  <div className="deck-label">GRAVE</div>
                </div>
              </div>
            </div>
          </div>

          <div className="board-row board-row-siege board-row-opponent">
            <div className="row-label">SIEGE</div>
            <div className="row-cards">
              {state.opponentRows.siege.map((card) => (
                <Card
                  key={card.id}
                  {...card}
                  size={CARD_SIZE}
                />
              ))}
            </div>
            <div className="row-score">{sumPower(state.opponentRows.siege)}</div>
          </div>

          <div className="board-row board-row-ranged board-row-opponent">
            <div className="row-label">RANGED</div>
            <div className="row-cards">
              {state.opponentRows.ranged.map((card) => (
                <Card
                  key={card.id}
                  {...card}
                  size={CARD_SIZE}
                />
              ))}
            </div>
            <div className="row-score">{sumPower(state.opponentRows.ranged)}</div>
          </div>

          <div className="board-row board-row-close board-row-opponent">
            <div className="row-label">CLOSE</div>
            <div className="row-cards">
              {state.opponentRows.close.map((card) => (
                <Card
                  key={card.id}
                  {...card}
                  size={CARD_SIZE}
                />
              ))}
            </div>
            <div className="row-score">{sumPower(state.opponentRows.close)}</div>
          </div>
        </div>

        <div className="board-divider" />

        <div className="board-side board-side-player">
          <div className="board-row board-row-close board-row-player">
            <div className="row-label">CLOSE</div>
            <div className="row-cards">
              {state.playerRows.close.map((card) => (
                <Card
                  key={card.id}
                  {...card}
                  size={CARD_SIZE}
                />
              ))}
            </div>
            <div className="row-score">{sumPower(state.playerRows.close)}</div>
          </div>

          <div className="board-row board-row-ranged board-row-player">
            <div className="row-label">RANGED</div>
            <div className="row-cards">
              {state.playerRows.ranged.map((card) => (
                <Card
                  key={card.id}
                  {...card}
                  size={CARD_SIZE}
                />
              ))}
            </div>
            <div className="row-score">{sumPower(state.playerRows.ranged)}</div>
          </div>

          <div className="board-row board-row-siege board-row-player">
            <div className="row-label">SIEGE</div>
            <div className="row-cards">
              {state.playerRows.siege.map((card) => (
                <Card
                  key={card.id}
                  {...card}
                  size={CARD_SIZE}
                />
              ))}
            </div>
            <div className="row-score">{sumPower(state.playerRows.siege)}</div>
          </div>

          <div className="board-meta board-meta-player">
            <div className="board-meta-left">
              <div className="board-leader-slot">
                <div className="board-leader-placeholder">
                  <div className="leader-label">LEADER</div>
                  <div className="leader-name">Robin's Cunning</div>
                </div>
              </div>

              <div className="board-score">
                <div className="score-rounds">
                  {Array.from({ length: 2 }).map((_, i) => (
                    <div
                      key={i}
                      className={`round-pip ${i < state.playerRoundsWon ? 'round-pip-won' : ''}`}
                    />
                  ))}
                </div>
                <div className="score-total">{state.playerScore}</div>
              </div>
            </div>

            <div className="board-meta-right">
              <div className="board-hand-info">
                <div className="hand-count">{state.playerHand.length}</div>
                <div className="hand-label">cards in hand</div>
              </div>

              <div className="board-deck-stack">
                <div className="deck-pile">
                  <div className="deck-count">{state.playerDeckSize}</div>
                  <div className="deck-label">DECK</div>
                </div>
                <div className="deck-pile">
                  <div className="deck-count">{state.playerDiscardSize}</div>
                  <div className="deck-label">GRAVE</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="board-hand">
          {state.playerHand.map((card) => (
            <Card
              key={card.id}
              {...card}
              size={HAND_CARD_SIZE}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

export default GameBoard;
