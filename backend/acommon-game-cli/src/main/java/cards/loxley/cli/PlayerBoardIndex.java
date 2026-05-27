package cards.loxley.cli;

import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.RowState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record PlayerBoardIndex(List<IndexedUnit> units) {

    private static final List<RowId> ROW_ORDER = List.of(RowId.CLOSE, RowId.RANGED, RowId.SIEGE);

    public PlayerBoardIndex {
        units = List.copyOf(units);
    }

    public record IndexedUnit(int index, CardInstance unit, RowId rowId) {
    }

    public static PlayerBoardIndex forPlayer(GameState state, Player player) {
        List<IndexedUnit> collected = new ArrayList<>();
        int next = 1;
        for (RowId rowId : ROW_ORDER) {
            RowState ownRow = state.playerState(player).board().row(rowId);
            for (CardInstance ci : ownRow.units()) {
                if (ci.owner() == player) {
                    collected.add(new IndexedUnit(next++, ci, rowId));
                }
            }
        }
        for (RowId rowId : ROW_ORDER) {
            RowState opponentRow = state.playerState(player.opponent()).board().row(rowId);
            for (CardInstance ci : opponentRow.units()) {
                if (ci.owner() == player) {
                    collected.add(new IndexedUnit(next++, ci, rowId));
                }
            }
        }
        return new PlayerBoardIndex(collected);
    }

    public Optional<IndexedUnit> find(int index) {
        if (index < 1 || index > units.size()) {
            return Optional.empty();
        }
        return Optional.of(units.get(index - 1));
    }

    public Optional<IndexedUnit> findByInstanceId(String instanceId) {
        for (IndexedUnit u : units) {
            if (u.unit().instanceId().equals(instanceId)) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return units.size();
    }
}
