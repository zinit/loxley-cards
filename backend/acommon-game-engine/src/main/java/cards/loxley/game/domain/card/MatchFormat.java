package cards.loxley.game.domain.card;

import java.util.List;

public record MatchFormat(
        int roundsToWin,
        int maxRounds,
        int startingHandSize,
        boolean drawEachTurn,
        int actionPerTurn,
        List<String> allowedActions
) {
}
