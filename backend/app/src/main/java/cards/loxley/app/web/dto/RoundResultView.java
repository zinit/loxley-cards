package cards.loxley.app.web.dto;

public record RoundResultView(
        int roundNumber,
        int p1Score,
        int p2Score,
        String winner
) {
}
