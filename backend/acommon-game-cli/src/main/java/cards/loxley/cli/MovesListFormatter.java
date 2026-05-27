package cards.loxley.cli;

import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveDescriber;
import cards.loxley.game.engine.move.PlayCardMove;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MovesListFormatter {

    private final MoveDescriber describer;

    public MovesListFormatter(MoveDescriber describer) {
        this.describer = describer;
    }

    public List<String> format(List<Move> moves, GameState state) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Move move : moves) {
            String key = dedupKey(move, state);
            Group existing = groups.get(key);
            if (existing == null) {
                groups.put(key, new Group(move, 1));
            } else {
                existing.count++;
            }
        }

        List<String> lines = new ArrayList<>();
        for (Group g : groups.values()) {
            String description = describer.describe(g.move, state);
            if (g.count > 1) {
                lines.add(description + "  (×" + g.count + " copies in hand)");
            } else {
                lines.add(description);
            }
        }
        return lines;
    }

    private String dedupKey(Move move, GameState state) {
        if (!(move instanceof PlayCardMove playMove)) {
            return move.getClass().getName() + "#" + System.identityHashCode(move);
        }
        Optional<String> cardName = state.playerState(playMove.player()).hand().stream()
                .filter(ci -> ci.instanceId().equals(playMove.handInstanceId()))
                .findFirst()
                .map(ci -> ci.card().name());
        String row = playMove.targetRow() == null ? "" : playMove.targetRow().name();
        String unit = playMove.targetInstanceId() == null ? "" : playMove.targetInstanceId();
        return cardName.orElse("?") + "__" + row + "__" + unit;
    }

    private static final class Group {
        final Move move;
        int count;

        Group(Move move, int count) {
            this.move = move;
            this.count = count;
        }
    }
}
