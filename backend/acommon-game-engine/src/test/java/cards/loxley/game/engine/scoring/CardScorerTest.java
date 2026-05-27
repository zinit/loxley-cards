package cards.loxley.game.engine.scoring;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.RowState;
import org.junit.jupiter.api.Test;

import static cards.loxley.game.engine.scoring.CardFixtures.cardWithAbilities;
import static cards.loxley.game.engine.scoring.CardFixtures.hero;
import static cards.loxley.game.engine.scoring.CardFixtures.instance;
import static cards.loxley.game.engine.scoring.CardFixtures.moraleBoost;
import static cards.loxley.game.engine.scoring.CardFixtures.plain;
import static cards.loxley.game.engine.scoring.CardFixtures.row;
import static cards.loxley.game.engine.scoring.CardFixtures.tightBond;
import static org.assertj.core.api.Assertions.assertThat;

class CardScorerTest {

    private final CardScorer scorer = new CardScorer();

    // ---------- Group A: single cards ----------

    @Test
    void plainCardAloneReturnsBasePower() {
        CardInstance ci = instance(plain("a", 5));
        RowState row = row(false, false, ci);

        assertThat(scorer.currentStrength(ci, row)).isEqualTo(5);
    }

    @Test
    void plainCardWithWeatherIsForcedToOne() {
        CardInstance ci = instance(plain("a", 5));
        RowState row = row(true, false, ci);

        assertThat(scorer.currentStrength(ci, row)).isEqualTo(1);
    }

    @Test
    void plainCardWithHornIsDoubled() {
        CardInstance ci = instance(plain("a", 5));
        RowState row = row(false, true, ci);

        assertThat(scorer.currentStrength(ci, row)).isEqualTo(10);
    }

    @Test
    void plainCardWithWeatherAndHornBecomesTwo() {
        CardInstance ci = instance(plain("a", 5));
        RowState row = row(true, true, ci);

        assertThat(scorer.currentStrength(ci, row)).isEqualTo(2);
    }

    @Test
    void heroAloneReturnsBasePower() {
        CardInstance ci = instance(hero(10));
        RowState row = row(false, false, ci);

        assertThat(scorer.currentStrength(ci, row)).isEqualTo(10);
    }

    @Test
    void heroWithWeatherIsImmune() {
        CardInstance ci = instance(hero(10));
        RowState row = row(true, false, ci);

        assertThat(scorer.currentStrength(ci, row)).isEqualTo(10);
    }

    @Test
    void heroWithHornIsImmune() {
        CardInstance ci = instance(hero(10));
        RowState row = row(false, true, ci);

        assertThat(scorer.currentStrength(ci, row)).isEqualTo(10);
    }

    @Test
    void heroWithWeatherAndHornIsImmune() {
        CardInstance ci = instance(hero(10));
        RowState row = row(true, true, ci);

        assertThat(scorer.currentStrength(ci, row)).isEqualTo(10);
    }

    // ---------- Group B: tight bond ----------

    @Test
    void tightBondSingleCardDoesNotActivate() {
        Card tb = tightBond("brothers", 4);
        CardInstance ci = instance(tb);
        RowState row = row(false, false, ci);

        assertThat(scorer.currentStrength(ci, row)).isEqualTo(4);
    }

    @Test
    void tightBondPairDoublesEachCard() {
        Card tb = tightBond("brothers", 4);
        CardInstance a = instance(tb);
        CardInstance b = instance(tb);
        RowState row = row(false, false, a, b);

        assertThat(scorer.currentStrength(a, row)).isEqualTo(8);
        assertThat(scorer.currentStrength(b, row)).isEqualTo(8);
    }

    @Test
    void tightBondTripleTriplesEachCard() {
        Card tb = tightBond("brothers", 4);
        CardInstance a = instance(tb);
        CardInstance b = instance(tb);
        CardInstance c = instance(tb);
        RowState row = row(false, false, a, b, c);

        assertThat(scorer.currentStrength(a, row)).isEqualTo(12);
        assertThat(scorer.currentStrength(b, row)).isEqualTo(12);
        assertThat(scorer.currentStrength(c, row)).isEqualTo(12);
    }

    @Test
    void tightBondTripleWithWeatherCappedAtMultiplierOverOne() {
        Card tb = tightBond("brothers", 4);
        CardInstance a = instance(tb);
        CardInstance b = instance(tb);
        CardInstance c = instance(tb);
        RowState row = row(true, false, a, b, c);

        assertThat(scorer.currentStrength(a, row)).isEqualTo(3);
    }

    @Test
    void tightBondTripleWithHornStacks() {
        Card tb = tightBond("brothers", 4);
        CardInstance a = instance(tb);
        CardInstance b = instance(tb);
        CardInstance c = instance(tb);
        RowState row = row(false, true, a, b, c);

        assertThat(scorer.currentStrength(a, row)).isEqualTo(24);
    }

    @Test
    void tightBondTripleWithWeatherAndHorn() {
        Card tb = tightBond("brothers", 4);
        CardInstance a = instance(tb);
        CardInstance b = instance(tb);
        CardInstance c = instance(tb);
        RowState row = row(true, true, a, b, c);

        assertThat(scorer.currentStrength(a, row)).isEqualTo(6);
    }

    @Test
    void tightBondPairsItsOwnCardIdOnly() {
        Card aCard = tightBond("a", 4);
        Card bCard = tightBond("b", 4);
        CardInstance a1 = instance(aCard);
        CardInstance a2 = instance(aCard);
        CardInstance b1 = instance(bCard);
        CardInstance b2 = instance(bCard);
        RowState row = row(false, false, a1, a2, b1, b2);

        assertThat(scorer.currentStrength(a1, row)).isEqualTo(8);
        assertThat(scorer.currentStrength(a2, row)).isEqualTo(8);
        assertThat(scorer.currentStrength(b1, row)).isEqualTo(8);
        assertThat(scorer.currentStrength(b2, row)).isEqualTo(8);
    }

    // ---------- Group C: morale boost ----------

    @Test
    void moraleBoostOnPlainCardAddsOne() {
        CardInstance booster = instance(moraleBoost(1));
        CardInstance other = instance(plain("plain", 4));
        RowState row = row(false, false, booster, other);

        assertThat(scorer.currentStrength(booster, row)).isEqualTo(1);
        assertThat(scorer.currentStrength(other, row)).isEqualTo(5);
    }

    @Test
    void twoMoraleBoostersBoostEachOther() {
        Card mb = moraleBoost(1);
        CardInstance a = instance(mb);
        CardInstance b = instance(mb);
        RowState row = row(false, false, a, b);

        assertThat(scorer.currentStrength(a, row)).isEqualTo(2);
        assertThat(scorer.currentStrength(b, row)).isEqualTo(2);
    }

    @Test
    void moraleBoostFromOtherDoesNotAffectHero() {
        CardInstance heroCi = instance(hero(10));
        CardInstance booster = instance(moraleBoost(1));
        RowState row = row(false, false, heroCi, booster);

        assertThat(scorer.currentStrength(heroCi, row)).isEqualTo(10);
    }

    // ---------- Group D: combos (per-card) ----------

    @Test
    void tightBondTripleWithExternalMoraleAndHornPerCard() {
        Card tb = tightBond("brothers", 4);
        CardInstance a = instance(tb);
        CardInstance b = instance(tb);
        CardInstance c = instance(tb);
        CardInstance booster = instance(moraleBoost(1));
        RowState row = row(false, true, a, b, c, booster);

        assertThat(scorer.currentStrength(a, row)).isEqualTo(26);
        assertThat(scorer.currentStrength(b, row)).isEqualTo(26);
        assertThat(scorer.currentStrength(c, row)).isEqualTo(26);
        assertThat(scorer.currentStrength(booster, row)).isEqualTo(2);
    }

    @Test
    void heroAndTripleTightBondUnderWeatherPerCard() {
        Card tb = tightBond("brothers", 4);
        CardInstance heroCi = instance(hero(10));
        CardInstance a = instance(tb);
        CardInstance b = instance(tb);
        CardInstance c = instance(tb);
        RowState row = row(true, false, heroCi, a, b, c);

        assertThat(scorer.currentStrength(heroCi, row)).isEqualTo(10);
        assertThat(scorer.currentStrength(a, row)).isEqualTo(3);
        assertThat(scorer.currentStrength(b, row)).isEqualTo(3);
        assertThat(scorer.currentStrength(c, row)).isEqualTo(3);
    }

    // ---------- Group F: edge cases ----------

    @Test
    void heroWithTightBondAbilityStillReturnsBasePower() {
        Card heroWithTb = cardWithAbilities("hero_tb", 10, AbilityCodes.HERO, AbilityCodes.TIGHT_BOND);
        CardInstance a = instance(heroWithTb);
        CardInstance b = instance(heroWithTb);
        RowState row = row(true, true, a, b);

        assertThat(scorer.currentStrength(a, row)).isEqualTo(10);
        assertThat(scorer.currentStrength(b, row)).isEqualTo(10);
    }

    @Test
    void heroWithMoraleBoostAbilityStillReturnsBasePower() {
        Card heroWithMb = cardWithAbilities("hero_mb", 10, AbilityCodes.HERO, AbilityCodes.MORALE_BOOST);
        CardInstance heroBooster = instance(heroWithMb);
        CardInstance plainCi = instance(plain("p", 4));
        RowState row = row(false, false, heroBooster, plainCi);

        assertThat(scorer.currentStrength(heroBooster, row)).isEqualTo(10);
        assertThat(scorer.currentStrength(plainCi, row)).isEqualTo(4);
    }

    @Test
    void scoringDoesNotMutateRowOrCardInstance() {
        CardInstance ci = instance(plain("a", 5));
        RowState row = row(true, true, ci);
        boolean weatherBefore = row.weatherActive();
        boolean hornBefore = row.hornActive();
        int unitCountBefore = row.unitCount();

        scorer.currentStrength(ci, row);

        assertThat(row.weatherActive()).isEqualTo(weatherBefore);
        assertThat(row.hornActive()).isEqualTo(hornBefore);
        assertThat(row.unitCount()).isEqualTo(unitCountBefore);
    }
}
