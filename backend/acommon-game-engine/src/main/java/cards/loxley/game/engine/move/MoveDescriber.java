package cards.loxley.game.engine.move;

import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MoveDescriber {

    private static final List<RowId> ROW_ORDER = List.of(RowId.CLOSE, RowId.RANGED, RowId.SIEGE);

    public String describe(Move move, GameState state) {
        return switch (move) {
            case PassMove m -> "PASS";
            case UseLeaderMove m -> describeLeader(state, m);
            case PlayCardMove m -> describePlayCard(state, m);
        };
    }

    private String describeLeader(GameState state, UseLeaderMove move) {
        CardInstance leader = state.playerState(move.player()).leader();
        return "USE_LEADER (" + leader.card().name() + ")";
    }

    private String describePlayCard(GameState state, PlayCardMove move) {
        PlayerState ps = state.playerState(move.player());
        Optional<CardInstance> handCard = ps.hand().stream()
                .filter(ci -> ci.instanceId().equals(move.handInstanceId()))
                .findFirst();
        String cardName = handCard.map(ci -> ci.card().name()).orElse("?");

        StringBuilder sb = new StringBuilder("PLAY ").append(cardName);

        String targetId = move.targetInstanceId();
        if (targetId != null) {
            Optional<TargetLookup> lookup = findOwnedUnit(state, move.player(), targetId);
            String targetName = lookup.map(t -> t.unit().card().name()).orElse("?");
            String rowName = lookup.map(t -> t.rowId().name()).orElse("?");
            sb.append(" on ").append(targetName).append(" (").append(rowName).append(")");
            lookup.ifPresent(t -> sb.append(" [u").append(t.index()).append("]"));
            return sb.toString();
        }

        RowId row = move.targetRow();
        if (row != null) {
            sb.append(" on ").append(row);
        }
        return sb.toString();
    }

    private Optional<TargetLookup> findOwnedUnit(GameState state, Player player, String instanceId) {
        int next = 1;
        for (RowId rowId : ROW_ORDER) {
            for (CardInstance ci : state.playerState(player).board().row(rowId).units()) {
                if (ci.owner() == player) {
                    if (ci.instanceId().equals(instanceId)) {
                        return Optional.of(new TargetLookup(next, ci, rowId));
                    }
                    next++;
                }
            }
        }
        for (RowId rowId : ROW_ORDER) {
            for (CardInstance ci : state.playerState(player.opponent()).board().row(rowId).units()) {
                if (ci.owner() == player) {
                    if (ci.instanceId().equals(instanceId)) {
                        return Optional.of(new TargetLookup(next, ci, rowId));
                    }
                    next++;
                }
            }
        }
        return Optional.empty();
    }

    private record TargetLookup(int index, CardInstance unit, RowId rowId) {
    }
}
