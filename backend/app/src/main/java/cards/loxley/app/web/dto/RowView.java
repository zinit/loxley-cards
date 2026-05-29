package cards.loxley.app.web.dto;

import java.util.List;

public record RowView(
        List<CardInstanceView> units,
        boolean weatherActive,
        boolean hornActive,
        int strength
) {
}
