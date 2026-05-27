package cards.loxley.game.engine.execution;

import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.BoardSide;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.RowState;
import cards.loxley.game.engine.event.MatchEnded;
import cards.loxley.game.engine.event.MatchEventBus;
import cards.loxley.game.engine.event.RoundEnded;
import cards.loxley.game.engine.scoring.BoardScorer;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RoundResolver {

    private final BoardScorer boardScorer;
    private final MatchEventBus eventBus;
    private final int roundsToWin;
    private final int maxRounds;

    public RoundResolver(BoardScorer boardScorer, GameDefinition definition, MatchEventBus eventBus) {
        this.boardScorer = boardScorer;
        this.eventBus = eventBus;
        this.roundsToWin = definition.ruleset().matchFormat().roundsToWin();
        this.maxRounds = definition.ruleset().matchFormat().maxRounds();
    }

    public void resolveEndOfRound(GameState state) {
        if (!state.bothPlayersPassed()) {
            throw new IllegalStateException(
                    "Round cannot be resolved unless both players have passed");
        }

        int p1Score = boardScorer.playerStrength(state, Player.P1);
        int p2Score = boardScorer.playerStrength(state, Player.P2);

        Optional<Player> roundWinner;
        if (p1Score > p2Score) {
            roundWinner = Optional.of(Player.P1);
            state.playerState(Player.P1).incrementRoundsWon();
        } else if (p2Score > p1Score) {
            roundWinner = Optional.of(Player.P2);
            state.playerState(Player.P2).incrementRoundsWon();
        } else {
            roundWinner = Optional.empty();
        }

        int finishedRoundNumber = state.roundNumber();
        state.recordRound(new GameState.RoundResult(
                finishedRoundNumber, p1Score, p2Score, roundWinner));

        eventBus.publish(
                new RoundEnded(finishedRoundNumber, p1Score, p2Score, roundWinner),
                state
        );

        int p1Rounds = state.playerState(Player.P1).roundsWon();
        int p2Rounds = state.playerState(Player.P2).roundsWon();

        if (p1Rounds >= roundsToWin) {
            finalizeMatchWithWinner(state, Player.P1);
            return;
        }
        if (p2Rounds >= roundsToWin) {
            finalizeMatchWithWinner(state, Player.P2);
            return;
        }
        if (state.roundNumber() >= maxRounds) {
            if (p1Rounds > p2Rounds) {
                finalizeMatchWithWinner(state, Player.P1);
            } else if (p2Rounds > p1Rounds) {
                finalizeMatchWithWinner(state, Player.P2);
            } else {
                finalizeMatchAsDraw(state);
            }
            return;
        }

        state.advanceRound();
        state.playerState(Player.P1).resetForNewRound();
        state.playerState(Player.P2).resetForNewRound();
        verifyCleanRoundStart(state);
        roundWinner.ifPresent(winner -> state.assignTurn(winner.opponent()));
    }

    private void finalizeMatchWithWinner(GameState state, Player winner) {
        state.endMatchWith(winner);
        eventBus.publish(
                new MatchEnded(Optional.of(winner), state.roundHistory().size()),
                state
        );
    }

    private void finalizeMatchAsDraw(GameState state) {
        state.endMatchAsDraw();
        eventBus.publish(
                new MatchEnded(Optional.empty(), state.roundHistory().size()),
                state
        );
    }

    private void verifyCleanRoundStart(GameState state) {
        if (state.playerState(Player.P1).passed() || state.playerState(Player.P2).passed()) {
            throw new IllegalStateException(
                    "Round " + state.roundNumber() + " began with a player still flagged as passed");
        }
        for (Player p : Player.values()) {
            BoardSide board = state.playerState(p).board();
            for (RowId rowId : RowId.values()) {
                RowState row = board.row(rowId);
                if (row.unitCount() != 0) {
                    throw new IllegalStateException(
                            "Row " + p + "/" + rowId + " was not cleared at the start of round "
                                    + state.roundNumber());
                }
                if (row.weatherActive()) {
                    throw new IllegalStateException(
                            "Weather still active on " + p + "/" + rowId
                                    + " at the start of round " + state.roundNumber());
                }
                if (row.hornActive()) {
                    throw new IllegalStateException(
                            "Horn still active on " + p + "/" + rowId
                                    + " at the start of round " + state.roundNumber());
                }
            }
        }
    }
}
