package cards.loxley.game.engine.ability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AbilityRegistry {

    private static final Logger log = LoggerFactory.getLogger(AbilityRegistry.class);

    private final Map<String, AbilityEffect> byCode;

    public AbilityRegistry(List<AbilityEffect> effects) {
        Map<String, AbilityEffect> index = new HashMap<>();
        for (AbilityEffect effect : effects) {
            AbilityEffect previous = index.put(effect.code(), effect);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate ability effect registered for code '" + effect.code()
                                + "': " + previous.getClass().getName()
                                + " and " + effect.getClass().getName());
            }
        }
        this.byCode = index;
    }

    public Optional<AbilityEffect> find(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public int size() {
        return byCode.size();
    }

    public void applyAll(List<String> codes, AbilityContext ctx) {
        for (String code : codes) {
            AbilityEffect effect = byCode.get(code);
            if (effect == null) {
                log.debug("No active effect registered for ability '{}' — treating as passive", code);
                continue;
            }
            effect.apply(ctx);
        }
    }
}
