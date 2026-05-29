package cards.loxley.app.web.dto;

import java.util.List;

public record CardInstanceView(
        String instanceId,
        String cardId,
        String name,
        String cardType,
        String row,
        Integer basePower,
        int currentStrength,
        List<String> abilities,
        String playTarget
) {
}
