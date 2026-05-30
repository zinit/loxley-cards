package cards.loxley.app.web.dto;

public record LastMoveView(
        String player,
        String kind,
        String cardName,
        String rowKind
) {
}
