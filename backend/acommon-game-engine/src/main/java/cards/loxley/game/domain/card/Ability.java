package cards.loxley.game.domain.card;

public record Ability(
        String code,
        String originalName,
        String polishName,
        String type,
        String target,
        String description,
        String implementationNotes
) {
}
