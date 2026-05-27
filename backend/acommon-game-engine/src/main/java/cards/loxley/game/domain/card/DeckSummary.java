package cards.loxley.game.domain.card;

public record DeckSummary(
        int leaderCount,
        int unitCardCopies,
        int specialCardCopies,
        int totalPlayableCardCopies
) {
}
