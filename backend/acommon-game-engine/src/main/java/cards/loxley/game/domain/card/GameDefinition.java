package cards.loxley.game.domain.card;

import java.util.List;

public record GameDefinition(
        String schemaVersion,
        Ruleset ruleset,
        List<Row> rows,
        List<Ability> abilities,
        List<Card> cards,
        Deck deck
) {
}
