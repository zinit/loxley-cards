package cards.loxley.cli;

import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.domain.state.RowState;
import cards.loxley.game.engine.scoring.BoardScorer;
import cards.loxley.game.engine.scoring.CardScorer;
import cards.loxley.game.engine.scoring.RowScorer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class BoardRenderer {

    private static final String HEAVY_LINE = "═".repeat(63);
    private static final String LIGHT_LINE = "─".repeat(63);
    private static final List<RowId> BOT_ROW_ORDER = List.of(RowId.SIEGE, RowId.RANGED, RowId.CLOSE);
    private static final List<RowId> PLAYER_ROW_ORDER = List.of(RowId.CLOSE, RowId.RANGED, RowId.SIEGE);

    private final CardScorer cardScorer;
    private final RowScorer rowScorer;
    private final BoardScorer boardScorer;

    public BoardRenderer(CardScorer cardScorer, RowScorer rowScorer, BoardScorer boardScorer) {
        this.cardScorer = cardScorer;
        this.rowScorer = rowScorer;
        this.boardScorer = boardScorer;
    }

    public String render(GameState state, Player perspective) {
        StringBuilder sb = new StringBuilder();
        PlayerState me = state.playerState(perspective);
        PlayerState bot = state.opponent(perspective);
        PlayerBoardIndex index = PlayerBoardIndex.forPlayer(state, perspective);

        sb.append(HEAVY_LINE).append(System.lineSeparator());
        sb.append(String.format(
                "    ROUND %d/3      Rounds won: YOU %d — BOT %d%n",
                state.roundNumber(), me.roundsWon(), bot.roundsWon()));
        sb.append(HEAVY_LINE).append(System.lineSeparator());
        sb.append(System.lineSeparator());

        sb.append(String.format(
                "BOT (hand: %d, deck: %d)              Total: %d%n",
                bot.hand().size(), bot.deck().size(), boardScorer.sideStrength(bot.board())));
        renderRows(sb, bot.board(), BOT_ROW_ORDER, index, perspective.opponent(), perspective);

        sb.append(LIGHT_LINE).append(System.lineSeparator());

        renderRows(sb, me.board(), PLAYER_ROW_ORDER, index, perspective, perspective);
        sb.append(String.format(
                "YOU (hand: %d, deck: %d)              Total: %d%n",
                me.hand().size(), me.deck().size(), boardScorer.sideStrength(me.board())));

        return sb.toString();
    }

    private void renderRows(StringBuilder sb, cards.loxley.game.domain.state.BoardSide board,
                            List<RowId> order, PlayerBoardIndex index,
                            Player physicalSide, Player perspective) {
        for (RowId rowId : order) {
            RowState row = board.row(rowId);
            sb.append(renderRow(rowId, row, index, physicalSide, perspective)).append(System.lineSeparator());
        }
    }

    private String renderRow(RowId rowId, RowState row, PlayerBoardIndex index,
                             Player physicalSide, Player perspective) {
        String cards = row.units().isEmpty()
                ? "—"
                : row.units().stream()
                        .map(ci -> formatUnit(ci, row, index, physicalSide, perspective))
                        .reduce((a, b) -> a + " " + b)
                        .orElse("—");

        String tags = "";
        if (row.weatherActive()) tags += "[W]";
        if (row.hornActive()) tags += "[H]";

        int strength = rowScorer.rowStrength(row);
        return String.format("%-7s| %-40s %4s = %d", rowId.name(), cards, tags, strength);
    }

    private String formatUnit(CardInstance ci, RowState row, PlayerBoardIndex index,
                              Player physicalSide, Player perspective) {
        int strength = cardScorer.currentStrength(ci, row);
        Optional<PlayerBoardIndex.IndexedUnit> indexed = index.findByInstanceId(ci.instanceId());
        boolean ownedByMe = ci.owner() == perspective;
        boolean onMySide = physicalSide == perspective;

        String tag;
        if (ownedByMe && !onMySide) {
            // My card living on opponent's board (i.e. my spy). Keep the [uN] index and annotate "mine".
            tag = indexed.map(u -> "[u" + u.index() + ", mine]").orElse("[mine]");
        } else if (!ownedByMe && onMySide) {
            // Opponent's card sitting on my side (typically their spy). Not in PlayerBoardIndex (no [uN]).
            tag = "[opp]";
        } else if (ownedByMe) {
            // My card on my side — the common case.
            tag = indexed.map(u -> "[u" + u.index() + "]").orElse("");
        } else {
            // Opponent's card on opponent's side — no tag.
            tag = "";
        }
        return ci.card().name() + "(" + strength + ")" + tag;
    }
}
