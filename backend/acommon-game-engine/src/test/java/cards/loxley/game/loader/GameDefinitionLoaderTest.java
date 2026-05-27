package cards.loxley.game.loader;

import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.DeckEntry;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.PlayTarget;
import cards.loxley.game.domain.card.RowId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GameDefinitionLoaderTest {

    private static final String REFERENCE_RESOURCE = "data/sherwood_reference_ruleset.json";

    @Autowired
    GameDefinitionLoader loader;

    @Test
    void loadsReferenceRulesetWithoutErrors() {
        GameDefinition def = loader.load(REFERENCE_RESOURCE);

        assertThat(def).isNotNull();
        assertThat(def.schemaVersion()).isEqualTo("0.1");
        assertThat(def.ruleset()).isNotNull();
        assertThat(def.rows()).isNotEmpty();
        assertThat(def.abilities()).isNotEmpty();
        assertThat(def.cards()).isNotEmpty();
        assertThat(def.deck()).isNotNull();
    }

    @Test
    void exposesExpectedDeckShape() {
        GameDefinition def = loader.load(REFERENCE_RESOURCE);

        assertThat(def.deck().leaderCardId()).isEqualTo("leader_robin_hood_sherwood_hunter");
        assertThat(def.cards()).hasSize(28);
        int totalDeckCopies = def.deck().cards().stream().mapToInt(DeckEntry::count).sum();
        assertThat(totalDeckCopies).isEqualTo(37);
    }

    @Test
    void deserializesRowIdEnumAndNullablePower() {
        GameDefinition def = loader.load(REFERENCE_RESOURCE);

        var brothers = def.cards().stream()
                .filter(c -> c.id().equals("sherwood_brothers"))
                .findFirst()
                .orElseThrow();
        assertThat(brothers.cardType()).isEqualTo(CardType.UNIT);
        assertThat(brothers.row()).isEqualTo(RowId.CLOSE);
        assertThat(brothers.basePower()).isEqualTo(4);
        assertThat(brothers.abilities()).containsExactly("TIGHT_BOND");
        assertThat(brothers.playTarget()).isEqualTo(PlayTarget.OWN_BOARD);

        var horn = def.cards().stream()
                .filter(c -> c.id().equals("sherwood_horn"))
                .findFirst()
                .orElseThrow();
        assertThat(horn.cardType()).isEqualTo(CardType.SPECIAL);
        assertThat(horn.row()).isNull();
        assertThat(horn.basePower()).isNull();
        assertThat(horn.playTarget()).isEqualTo(PlayTarget.SELECTED_ROW);
    }
}
