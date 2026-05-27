package cards.loxley.game.domain.card;

public record Ruleset(
        String id,
        String name,
        String description,
        MatchFormat matchFormat
) {
}
