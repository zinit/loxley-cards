package cards.loxley.game.engine.faction;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class FactionPassiveRegistry {

    public static final String SHERWOOD_OUTLAWS = "SHERWOOD_OUTLAWS";

    private final Map<String, FactionPassive> byFaction;

    public FactionPassiveRegistry() {
        Map<String, FactionPassive> map = new HashMap<>();
        map.put(SHERWOOD_OUTLAWS, new FactionPassive(
                SHERWOOD_OUTLAWS,
                FactionPassive.DRAW_CARDS_ON_ROUND_WIN,
                Map.of("count", 1)
        ));
        this.byFaction = Map.copyOf(map);
    }

    public Optional<FactionPassive> findByFaction(String factionId) {
        return Optional.ofNullable(byFaction.get(factionId));
    }
}
