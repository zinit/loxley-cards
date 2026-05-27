package cards.loxley.game.engine.ability.effects;

import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.ability.AbilityEffect;
import cards.loxley.game.engine.scoring.AbilityCodes;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

@Component
public class MedicEffect implements AbilityEffect {

    @Override
    public String code() {
        return AbilityCodes.MEDIC;
    }

    @Override
    public void apply(AbilityContext ctx) {
        PlayerState self = ctx.selfState();

        Optional<CardInstance> target = self.graveyard().stream()
                .filter(ci -> ci.card().cardType() == CardType.UNIT)
                .filter(ci -> !ci.card().abilities().contains(AbilityCodes.HERO))
                .max(Comparator.comparingInt(ci -> ci.card().basePower()));

        if (target.isEmpty()) {
            return;
        }

        CardInstance revived = target.get();
        self.removeFromGraveyard(revived.instanceId());
        self.board().row(revived.card().row()).addUnit(revived);

        AbilityContext nested = new AbilityContext(
                ctx.state(),
                ctx.player(),
                revived,
                null,
                ctx.registry()
        );
        ctx.registry().applyAll(revived.card().abilities(), nested);
    }
}
