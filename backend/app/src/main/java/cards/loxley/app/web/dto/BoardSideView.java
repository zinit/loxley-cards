package cards.loxley.app.web.dto;

public record BoardSideView(
        RowView close,
        RowView ranged,
        RowView siege,
        int totalStrength
) {
}
