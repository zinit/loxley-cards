package cards.loxley.game.engine.faction;

import java.util.Map;

public record FactionPassive(
        String factionId,
        String type,
        Map<String, Object> params
) {

    public static final String DRAW_CARDS_ON_ROUND_WIN = "DRAW_CARDS_ON_ROUND_WIN";
}
