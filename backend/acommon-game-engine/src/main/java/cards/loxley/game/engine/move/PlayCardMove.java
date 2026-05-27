package cards.loxley.game.engine.move;

import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.Player;

public record PlayCardMove(
        Player player,
        String handInstanceId,
        RowId targetRow,
        String targetInstanceId
) implements Move {

    public static PlayCardMove unit(Player player, String handInstanceId, RowId targetRow) {
        return new PlayCardMove(player, handInstanceId, targetRow, null);
    }

    public static PlayCardMove special(Player player, String handInstanceId) {
        return new PlayCardMove(player, handInstanceId, null, null);
    }

    public static PlayCardMove specialOnRow(Player player, String handInstanceId, RowId targetRow) {
        return new PlayCardMove(player, handInstanceId, targetRow, null);
    }

    public static PlayCardMove specialOnUnit(Player player, String handInstanceId, String targetInstanceId) {
        return new PlayCardMove(player, handInstanceId, null, targetInstanceId);
    }

    public static PlayCardMove spy(Player player, String handInstanceId, RowId targetRow) {
        return new PlayCardMove(player, handInstanceId, targetRow, null);
    }
}
