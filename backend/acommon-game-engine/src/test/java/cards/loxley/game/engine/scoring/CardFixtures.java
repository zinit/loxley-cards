package cards.loxley.game.engine.scoring;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.MvpImportance;
import cards.loxley.game.domain.card.PlayTarget;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.RowState;

import java.util.List;

final class CardFixtures {

    private CardFixtures() {
    }

    static Card hero(int power) {
        return card("hero_" + power, power, List.of(AbilityCodes.HERO));
    }

    static Card tightBond(String id, int power) {
        return card(id, power, List.of(AbilityCodes.TIGHT_BOND));
    }

    static Card moraleBoost(int power) {
        return card("morale_" + power, power, List.of(AbilityCodes.MORALE_BOOST));
    }

    static Card plain(String id, int power) {
        return card(id, power, List.of());
    }

    static Card cardWithAbilities(String id, int power, String... abilities) {
        return card(id, power, List.of(abilities));
    }

    static CardInstance instance(Card card) {
        return new CardInstance(card, Player.P1);
    }

    static RowState row(boolean weather, boolean horn, CardInstance... units) {
        return rowOn(RowId.CLOSE, weather, horn, units);
    }

    static RowState rowOn(RowId rowId, boolean weather, boolean horn, CardInstance... units) {
        RowState row = new RowState(rowId);
        if (weather) {
            row.applyWeather();
        }
        if (horn) {
            row.applyHorn();
        }
        for (CardInstance unit : units) {
            row.addUnit(unit);
        }
        return row;
    }

    private static Card card(String id, int power, List<String> abilities) {
        return new Card(
                id,
                "Test " + id,
                CardType.UNIT,
                "TEST_FACTION",
                "Test card",
                RowId.CLOSE,
                power,
                abilities,
                PlayTarget.OWN_BOARD,
                "test_role",
                MvpImportance.OPTIONAL
        );
    }
}
