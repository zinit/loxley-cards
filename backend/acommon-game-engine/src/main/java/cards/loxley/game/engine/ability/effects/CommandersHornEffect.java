package cards.loxley.game.engine.ability.effects;

import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.ability.AbilityEffect;
import cards.loxley.game.engine.scoring.AbilityCodes;
import org.springframework.stereotype.Component;

@Component
public class CommandersHornEffect implements AbilityEffect {

    @Override
    public String code() {
        return AbilityCodes.COMMANDERS_HORN;
    }

    @Override
    public void apply(AbilityContext ctx) {
        if (ctx.move() == null || ctx.move().targetRow() == null) {
            throw new IllegalStateException(
                    "COMMANDERS_HORN requires a PlayCardMove with a target row");
        }
        ctx.selfState().board().row(ctx.move().targetRow()).applyHorn();
    }
}
