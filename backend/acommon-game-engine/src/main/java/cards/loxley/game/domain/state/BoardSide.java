package cards.loxley.game.domain.state;

import cards.loxley.game.domain.card.RowId;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class BoardSide {

    private final Map<RowId, RowState> rows;

    public BoardSide() {
        EnumMap<RowId, RowState> initial = new EnumMap<>(RowId.class);
        for (RowId id : RowId.values()) {
            initial.put(id, new RowState(id));
        }
        this.rows = initial;
    }

    public RowState row(RowId id) {
        return rows.get(id);
    }

    public RowState close() {
        return rows.get(RowId.CLOSE);
    }

    public RowState ranged() {
        return rows.get(RowId.RANGED);
    }

    public RowState siege() {
        return rows.get(RowId.SIEGE);
    }

    public void clearAll() {
        rows.values().forEach(RowState::clear);
    }

    public Optional<CardInstance> findUnit(String instanceId) {
        for (RowState row : rows.values()) {
            Optional<CardInstance> found = row.findUnit(instanceId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public boolean removeUnit(String instanceId) {
        for (RowState row : rows.values()) {
            if (row.removeUnit(instanceId)) {
                return true;
            }
        }
        return false;
    }
}
