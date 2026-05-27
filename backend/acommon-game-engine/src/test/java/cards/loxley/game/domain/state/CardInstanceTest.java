package cards.loxley.game.domain.state;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.MvpImportance;
import cards.loxley.game.domain.card.PlayTarget;
import cards.loxley.game.domain.card.RowId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CardInstanceTest {

    private static final Card SAMPLE_CARD = new Card(
            "sherwood_brothers",
            "Bracia z Sherwood",
            CardType.UNIT,
            "SHERWOOD_OUTLAWS",
            "Test card.",
            RowId.CLOSE,
            4,
            List.of("TIGHT_BOND"),
            PlayTarget.OWN_BOARD,
            "core_combo",
            MvpImportance.REQUIRED
    );

    @Test
    void twoInstancesOfSameCardHaveDifferentInstanceIds() {
        CardInstance a = new CardInstance(SAMPLE_CARD, Player.P1);
        CardInstance b = new CardInstance(SAMPLE_CARD, Player.P1);

        assertThat(a.instanceId()).isNotEqualTo(b.instanceId());
        assertThat(a.card()).isSameAs(b.card());
    }

    @Test
    void equalsAndHashCodeUseInstanceId() {
        CardInstance a = new CardInstance(SAMPLE_CARD, Player.P1);
        CardInstance b = new CardInstance(SAMPLE_CARD, Player.P1);

        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(a.instanceId().hashCode());
    }

    @Test
    void toStringContainsCardIdAndEightCharPrefix() {
        CardInstance ci = new CardInstance(SAMPLE_CARD, Player.P1);

        String repr = ci.toString();
        String expectedPrefix = ci.instanceId().substring(0, 8);
        assertThat(repr).isEqualTo("sherwood_brothers#" + expectedPrefix);
    }

    @Test
    void oneThousandInstancesAllHaveUniqueIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(new CardInstance(SAMPLE_CARD, Player.P1).instanceId());
        }
        assertThat(ids).hasSize(1000);
    }
}
