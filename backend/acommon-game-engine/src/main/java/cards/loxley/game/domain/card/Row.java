package cards.loxley.game.domain.card;

public record Row(
        String code,
        RowId id,
        String originalName,
        String polishName,
        String description
) {
}
