package cards.loxley.app.web.dto;

import java.util.List;

public record PlayerView(
        BoardSideView board,
        List<CardInstanceView> hand,
        int deckSize,
        int graveyardSize,
        boolean leaderUsed,
        boolean passed,
        int roundsWon,
        int totalStrength
) {
}
