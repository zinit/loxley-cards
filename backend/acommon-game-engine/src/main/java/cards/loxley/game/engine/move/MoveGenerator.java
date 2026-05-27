package cards.loxley.game.engine.move;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.scoring.AbilityCodes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MoveGenerator {

    public List<Move> legalMoves(GameState state, Player player) {
        PlayerState ps = state.playerState(player);

        if (ps.passed()) {
            return List.of();
        }

        List<Move> moves = new ArrayList<>();
        moves.add(new PassMove(player));

        if (!ps.leaderUsed()) {
            moves.add(new UseLeaderMove(player));
        }

        for (CardInstance ci : ps.hand()) {
            Card card = ci.card();
            switch (card.cardType()) {
                case UNIT -> generateUnitMoves(moves, player, ci);
                case SPECIAL -> generateSpecialMoves(moves, state, player, ci);
                case LEADER -> {
                    // Leader cards are not held in hand; ignore if encountered.
                }
            }
        }

        return moves;
    }

    private void generateUnitMoves(List<Move> moves, Player player, CardInstance ci) {
        Card card = ci.card();
        if (card.abilities().contains(AbilityCodes.SPY)) {
            moves.add(PlayCardMove.spy(player, ci.instanceId(), card.row()));
        } else {
            moves.add(PlayCardMove.unit(player, ci.instanceId(), card.row()));
        }
    }

    private void generateSpecialMoves(List<Move> moves, GameState state, Player player, CardInstance ci) {
        Card card = ci.card();
        switch (card.playTarget()) {
            case GLOBAL -> moves.add(PlayCardMove.special(player, ci.instanceId()));
            case SELECTED_ROW -> {
                for (RowId row : RowId.values()) {
                    moves.add(PlayCardMove.specialOnRow(player, ci.instanceId(), row));
                }
            }
            case OWN_UNIT_ON_BOARD -> {
                // Any non-hero unit physically on my side of the board — including opponent's spies.
                for (RowId row : RowId.values()) {
                    for (CardInstance target : state.playerState(player).board().row(row).units()) {
                        if (target.card().abilities().contains(AbilityCodes.HERO)) {
                            continue;
                        }
                        moves.add(PlayCardMove.specialOnUnit(player, ci.instanceId(), target.instanceId()));
                    }
                }
                // PLUS: my own units that physically live on the opponent's side (i.e. my spies).
                Player opponent = player.opponent();
                for (RowId row : RowId.values()) {
                    for (CardInstance target : state.playerState(opponent).board().row(row).units()) {
                        if (target.owner() != player) {
                            continue;
                        }
                        if (target.card().abilities().contains(AbilityCodes.HERO)) {
                            continue;
                        }
                        moves.add(PlayCardMove.specialOnUnit(player, ci.instanceId(), target.instanceId()));
                    }
                }
            }
            case OWN_BOARD, OPPONENT_BOARD -> {
                // Not used for SPECIAL cards in the reference ruleset; no-op.
            }
        }
    }
}
