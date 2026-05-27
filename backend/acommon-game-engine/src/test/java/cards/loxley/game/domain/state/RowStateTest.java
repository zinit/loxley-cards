package cards.loxley.game.domain.state;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.MvpImportance;
import cards.loxley.game.domain.card.PlayTarget;
import cards.loxley.game.domain.card.RowId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RowStateTest {

    private static final Card UNIT_CARD = new Card(
            "sherwood_brothers", "Bracia", CardType.UNIT, "SHERWOOD_OUTLAWS",
            "desc", RowId.CLOSE, 4, List.of("TIGHT_BOND"),
            PlayTarget.OWN_BOARD, "core_combo", MvpImportance.REQUIRED
    );

    @Test
    void addUnitIncreasesCountAndFindFinds() {
        RowState row = new RowState(RowId.CLOSE);
        CardInstance ci = new CardInstance(UNIT_CARD, Player.P1);

        row.addUnit(ci);

        assertThat(row.unitCount()).isEqualTo(1);
        assertThat(row.findUnit(ci.instanceId())).contains(ci);
    }

    @Test
    void removeUnitReturnsTrueAndRemovesWhenPresent() {
        RowState row = new RowState(RowId.CLOSE);
        CardInstance ci = new CardInstance(UNIT_CARD, Player.P1);
        row.addUnit(ci);

        boolean removed = row.removeUnit(ci.instanceId());

        assertThat(removed).isTrue();
        assertThat(row.unitCount()).isZero();
        assertThat(row.findUnit(ci.instanceId())).isEmpty();
    }

    @Test
    void removeUnitReturnsFalseWhenAbsent() {
        RowState row = new RowState(RowId.CLOSE);

        boolean removed = row.removeUnit("nonexistent");

        assertThat(removed).isFalse();
    }

    @Test
    void clearRemovesUnitsAndKeepsWeatherAndHornFalse() {
        RowState row = new RowState(RowId.CLOSE);
        row.addUnit(new CardInstance(UNIT_CARD, Player.P1));
        row.addUnit(new CardInstance(UNIT_CARD, Player.P1));

        row.clear();

        assertThat(row.unitCount()).isZero();
        assertThat(row.weatherActive()).isFalse();
        assertThat(row.hornActive()).isFalse();
    }

    @Test
    void unitsGetterIsUnmodifiable() {
        RowState row = new RowState(RowId.CLOSE);
        CardInstance ci = new CardInstance(UNIT_CARD, Player.P1);
        row.addUnit(ci);

        List<CardInstance> exposed = row.units();

        assertThatThrownBy(() -> exposed.add(new CardInstance(UNIT_CARD, Player.P1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
