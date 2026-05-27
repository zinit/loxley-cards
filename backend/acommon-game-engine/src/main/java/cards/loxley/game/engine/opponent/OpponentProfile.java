package cards.loxley.game.engine.opponent;

public record OpponentProfile(
        String id,
        String displayName,
        String strategyName,
        String deckVariantId,
        String description
) {
}
