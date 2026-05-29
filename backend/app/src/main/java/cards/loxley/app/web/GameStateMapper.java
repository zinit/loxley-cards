package cards.loxley.app.web;

import cards.loxley.app.web.dto.*;
import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.*;
import cards.loxley.game.engine.move.*;
import cards.loxley.game.engine.scoring.AbilityCodes;
import cards.loxley.game.engine.scoring.BoardScorer;
import cards.loxley.game.engine.scoring.CardScorer;
import cards.loxley.game.engine.scoring.RowScorer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameStateMapper {

    private final CardScorer cardScorer;
    private final RowScorer rowScorer;
    private final BoardScorer boardScorer;
    private final MoveDescriber moveDescriber;

    public GameStateMapper(CardScorer cardScorer, RowScorer rowScorer,
                           BoardScorer boardScorer, MoveDescriber moveDescriber) {
        this.cardScorer = cardScorer;
        this.rowScorer = rowScorer;
        this.boardScorer = boardScorer;
        this.moveDescriber = moveDescriber;
    }

    public GameStateView toView(GameState state, String gameId, Player perspective,
                                List<Move> legalMoves) {
        Player opponent = perspective.opponent();
        return new GameStateView(
                gameId,
                state.roundNumber(),
                state.currentTurn().name(),
                state.currentTurn() == perspective,
                state.matchEnded(),
                state.matchWinner().map(Enum::name).orElse(null),
                toPlayerView(state, perspective),
                toOpponentView(state, opponent),
                state.roundHistory().stream().map(this::toRoundResultView).toList(),
                legalMoves.stream().map(m -> toMoveView(m, state)).toList()
        );
    }

    private PlayerView toPlayerView(GameState state, Player player) {
        PlayerState ps = state.playerState(player);
        return new PlayerView(
                toBoardSideView(ps.board()),
                ps.hand().stream().map(ci -> toCardInstanceView(ci, null)).toList(),
                ps.deck().size(),
                ps.graveyard().size(),
                ps.leaderUsed(),
                ps.passed(),
                ps.roundsWon(),
                boardScorer.sideStrength(ps.board())
        );
    }

    private OpponentView toOpponentView(GameState state, Player player) {
        PlayerState ps = state.playerState(player);
        return new OpponentView(
                toBoardSideView(ps.board()),
                ps.hand().size(),
                ps.deck().size(),
                ps.graveyard().size(),
                ps.leaderUsed(),
                ps.passed(),
                ps.roundsWon(),
                boardScorer.sideStrength(ps.board())
        );
    }

    private BoardSideView toBoardSideView(BoardSide board) {
        return new BoardSideView(
                toRowView(board.close()),
                toRowView(board.ranged()),
                toRowView(board.siege()),
                rowScorer.rowStrength(board.close())
                        + rowScorer.rowStrength(board.ranged())
                        + rowScorer.rowStrength(board.siege())
        );
    }

    private RowView toRowView(RowState row) {
        return new RowView(
                row.units().stream()
                        .map(ci -> toCardInstanceView(ci, row))
                        .toList(),
                row.weatherActive(),
                row.hornActive(),
                rowScorer.rowStrength(row)
        );
    }

    private CardInstanceView toCardInstanceView(CardInstance ci, RowState row) {
        Card card = ci.card();
        int strength = row != null
                ? cardScorer.currentStrength(ci, row)
                : (card.basePower() != null ? card.basePower() : 0);
        return new CardInstanceView(
                ci.instanceId(),
                card.id(),
                card.name(),
                card.cardType().name(),
                card.row() != null ? card.row().name() : null,
                card.basePower(),
                strength,
                card.abilities(),
                card.playTarget() != null ? card.playTarget().name() : null
        );
    }

    private MoveView toMoveView(Move move, GameState state) {
        String description = moveDescriber.describe(move, state);
        return switch (move) {
            case PassMove m -> new MoveView(
                    "PASS", null, null, null, null, description);
            case UseLeaderMove m -> new MoveView(
                    "LEADER", null, null, null, null, description);
            case PlayCardMove m -> toPlayCardMoveView(m, state, description);
        };
    }

    private MoveView toPlayCardMoveView(PlayCardMove m, GameState state,
                                        String description) {
        PlayerState ps = state.playerState(m.player());
        CardInstance handCard = ps.hand().stream()
                .filter(ci -> ci.instanceId().equals(m.handInstanceId()))
                .findFirst()
                .orElse(null);

        String cardName = handCard != null ? handCard.card().name() : null;
        String targetRow = m.targetRow() != null ? m.targetRow().name() : null;
        String kind = determinePlayCardKind(m, handCard);

        return new MoveView(kind, m.handInstanceId(), cardName, targetRow,
                m.targetInstanceId(), description);
    }

    private String determinePlayCardKind(PlayCardMove m, CardInstance handCard) {
        if (handCard != null && handCard.card().cardType() == CardType.UNIT) {
            if (handCard.card().abilities().contains(AbilityCodes.SPY)) {
                return "SPY";
            }
            return "UNIT";
        }
        // SPECIAL card
        if (m.targetInstanceId() != null) {
            return "UNIT_TARGET";
        }
        if (m.targetRow() != null) {
            return "ROW";
        }
        return "SPECIAL";
    }

    private RoundResultView toRoundResultView(GameState.RoundResult rr) {
        return new RoundResultView(
                rr.roundNumber(),
                rr.p1Score(),
                rr.p2Score(),
                rr.winner().map(Enum::name).orElse(null)
        );
    }
}
