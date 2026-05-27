package cards.loxley.game.engine.scoring;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.RowState;
import org.junit.jupiter.api.Test;

import static cards.loxley.game.engine.scoring.CardFixtures.hero;
import static cards.loxley.game.engine.scoring.CardFixtures.instance;
import static cards.loxley.game.engine.scoring.CardFixtures.moraleBoost;
import static cards.loxley.game.engine.scoring.CardFixtures.row;
import static cards.loxley.game.engine.scoring.CardFixtures.tightBond;
import static org.assertj.core.api.Assertions.assertThat;

class RowScorerTest {

    private final RowScorer rowScorer = new RowScorer(new CardScorer());

    @Test
    void emptyRowHasZeroStrength() {
        RowState row = row(false, false);

        assertThat(rowScorer.rowStrength(row)).isZero();
    }

    @Test
    void singleHeroRowEqualsHeroBasePower() {
        CardInstance heroCi = instance(hero(10));
        RowState row = row(false, false, heroCi);

        assertThat(rowScorer.rowStrength(row)).isEqualTo(10);
    }

    @Test
    void tightBondPairSumsToSixteen() {
        Card tb = tightBond("brothers", 4);
        RowState row = row(false, false, instance(tb), instance(tb));

        assertThat(rowScorer.rowStrength(row)).isEqualTo(16);
    }

    @Test
    void tightBondTripleSumsToThirtySix() {
        Card tb = tightBond("brothers", 4);
        RowState row = row(false, false, instance(tb), instance(tb), instance(tb));

        assertThat(rowScorer.rowStrength(row)).isEqualTo(36);
    }

    @Test
    void tightBondTripleUnderWeatherSumsToNine() {
        Card tb = tightBond("brothers", 4);
        RowState row = row(true, false, instance(tb), instance(tb), instance(tb));

        assertThat(rowScorer.rowStrength(row)).isEqualTo(9);
    }

    @Test
    void tightBondTripleUnderHornSumsToSeventyTwo() {
        Card tb = tightBond("brothers", 4);
        RowState row = row(false, true, instance(tb), instance(tb), instance(tb));

        assertThat(rowScorer.rowStrength(row)).isEqualTo(72);
    }

    @Test
    void tightBondTripleUnderWeatherAndHornSumsToEighteen() {
        Card tb = tightBond("brothers", 4);
        RowState row = row(true, true, instance(tb), instance(tb), instance(tb));

        assertThat(rowScorer.rowStrength(row)).isEqualTo(18);
    }

    @Test
    void tightBondTripleWithMoraleAndHornSumsToEighty() {
        Card tb = tightBond("brothers", 4);
        RowState row = row(
                false, true,
                instance(tb), instance(tb), instance(tb),
                instance(moraleBoost(1))
        );

        assertThat(rowScorer.rowStrength(row)).isEqualTo(80);
    }

    @Test
    void heroPlusThreeTightBondUnderWeatherSumsToNineteen() {
        Card tb = tightBond("brothers", 4);
        RowState row = row(
                true, false,
                instance(hero(10)),
                instance(tb), instance(tb), instance(tb)
        );

        assertThat(rowScorer.rowStrength(row)).isEqualTo(19);
    }
}
