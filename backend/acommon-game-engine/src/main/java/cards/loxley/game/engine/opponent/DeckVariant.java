package cards.loxley.game.engine.opponent;

import cards.loxley.game.domain.card.DeckEntry;

import java.util.List;

public record DeckVariant(
        String id,
        String displayName,
        String leaderCardId,
        List<DeckEntry> cards
) {
    public int totalCardCopies() {
        return cards.stream().mapToInt(DeckEntry::count).sum();
    }
}
