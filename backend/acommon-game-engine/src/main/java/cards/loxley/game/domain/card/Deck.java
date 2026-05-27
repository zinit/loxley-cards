package cards.loxley.game.domain.card;

import java.util.List;

public record Deck(
        String id,
        String name,
        String faction,
        String leaderCardId,
        List<DeckEntry> cards,
        DeckSummary summary
) {
}
