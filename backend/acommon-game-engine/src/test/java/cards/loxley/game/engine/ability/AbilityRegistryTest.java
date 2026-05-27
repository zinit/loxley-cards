package cards.loxley.game.engine.ability;

import cards.loxley.game.engine.scoring.AbilityCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class AbilityRegistryTest {

    @Autowired
    AbilityRegistry registry;

    @Test
    void registersAllExpectedActiveEffects() {
        assertThat(registry.size()).isEqualTo(8);
        assertThat(registry.find(AbilityCodes.WEATHER_CLOSE)).isPresent();
        assertThat(registry.find(AbilityCodes.WEATHER_RANGED)).isPresent();
        assertThat(registry.find(AbilityCodes.WEATHER_SIEGE)).isPresent();
        assertThat(registry.find(AbilityCodes.CLEAR_WEATHER)).isPresent();
        assertThat(registry.find(AbilityCodes.COMMANDERS_HORN)).isPresent();
        assertThat(registry.find(AbilityCodes.SCORCH)).isPresent();
        assertThat(registry.find(AbilityCodes.SPY)).isPresent();
        assertThat(registry.find(AbilityCodes.MEDIC)).isPresent();
    }

    @Test
    void passiveAbilitiesAreNotRegistered() {
        assertThat(registry.find(AbilityCodes.HERO)).isEmpty();
        assertThat(registry.find(AbilityCodes.TIGHT_BOND)).isEmpty();
        assertThat(registry.find(AbilityCodes.MORALE_BOOST)).isEmpty();
        assertThat(registry.find(AbilityCodes.DECOY)).isEmpty();
    }

    @Test
    void applyAllSilentlySkipsUnknownCodes() {
        AbilityContext ctx = new AbilityContext(null, null, null, null, registry);

        assertThatCode(() -> registry.applyAll(
                List.of(AbilityCodes.HERO, AbilityCodes.TIGHT_BOND, "TOTALLY_FAKE"), ctx))
                .doesNotThrowAnyException();
    }
}
