package cards.loxley.app.web.dto;

public record MoveView(
        String kind,
        String handInstanceId,
        String cardName,
        String targetRow,
        String targetInstanceId,
        String description
) {
}
