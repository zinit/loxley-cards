package cards.loxley.game.engine.ability.effects;

import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.ability.AbilityEffect;
import cards.loxley.game.engine.scoring.AbilityCodes;
import org.springframework.stereotype.Component;

@Component
public class SpyEffect implements AbilityEffect {

    private static final int SPY_DRAW_COUNT = 2;

    @Override
    public String code() {
        return AbilityCodes.SPY;
    }

    @Override
    public void apply(AbilityContext ctx) {
        ctx.selfState().drawCards(SPY_DRAW_COUNT);
    }
}
