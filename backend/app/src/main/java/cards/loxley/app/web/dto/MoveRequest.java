package cards.loxley.app.web.dto;

public record MoveRequest(
        String kind,
        String handInstanceId,
        String targetRow,
        String targetInstanceId
) {
}
