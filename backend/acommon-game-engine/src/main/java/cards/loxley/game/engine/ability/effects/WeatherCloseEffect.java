package cards.loxley.game.engine.ability.effects;

import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.ability.AbilityEffect;
import cards.loxley.game.engine.scoring.AbilityCodes;
import org.springframework.stereotype.Component;

@Component
public class WeatherCloseEffect implements AbilityEffect {

    @Override
    public String code() {
        return AbilityCodes.WEATHER_CLOSE;
    }

    @Override
    public void apply(AbilityContext ctx) {
        for (Player p : Player.values()) {
            ctx.state().playerState(p).board().row(RowId.CLOSE).applyWeather();
        }
    }
}
