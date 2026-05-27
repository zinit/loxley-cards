package cards.loxley.game.engine.opponent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OpponentProfileRegistryTest {

    @Autowired
    OpponentProfileRegistry registry;

    @Test
    void initializesAllFiveProfiles() {
        List<OpponentProfile> all = registry.all();
        assertThat(all).hasSize(5);
        assertThat(all.stream().map(OpponentProfile::id))
                .containsExactly("ultra_easy", "easy", "medium", "hard", "top_hard");
    }

    @Test
    void findByIdReturnsExpectedProfileForEachId() {
        assertThat(registry.findById("ultra_easy"))
                .hasValueSatisfying(p -> {
                    assertThat(p.strategyName()).isEqualTo("random");
                    assertThat(p.deckVariantId()).isEqualTo("gimped_deck");
                });
        assertThat(registry.findById("easy"))
                .hasValueSatisfying(p -> {
                    assertThat(p.strategyName()).isEqualTo("random");
                    assertThat(p.deckVariantId()).isEqualTo("gimped_deck");
                });
        assertThat(registry.findById("medium"))
                .hasValueSatisfying(p -> {
                    assertThat(p.strategyName()).isEqualTo("heuristic-easy");
                    assertThat(p.deckVariantId()).isEqualTo("standard_deck");
                });
        assertThat(registry.findById("hard"))
                .hasValueSatisfying(p -> {
                    assertThat(p.strategyName()).isEqualTo("heuristic-medium");
                    assertThat(p.deckVariantId()).isEqualTo("boosted_deck");
                });
        assertThat(registry.findById("top_hard"))
                .hasValueSatisfying(p -> {
                    assertThat(p.strategyName()).isEqualTo("heuristic-medium");
                    assertThat(p.deckVariantId()).isEqualTo("boosted_plus_deck");
                });
    }

    @Test
    void findByIdReturnsEmptyForUnknownProfile() {
        assertThat(registry.findById("nonexistent")).isEmpty();
    }
}
