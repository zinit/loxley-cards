package cards.loxley.app.web.dto;

public record OpponentView(
        BoardSideView board,
        int handSize,
        int deckSize,
        int graveyardSize,
        boolean leaderUsed,
        boolean passed,
        int roundsWon,
        int totalStrength
) {
}
