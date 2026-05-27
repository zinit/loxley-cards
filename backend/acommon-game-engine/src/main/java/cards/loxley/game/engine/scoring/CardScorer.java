package cards.loxley.game.engine.scoring;

import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.RowState;
import org.springframework.stereotype.Component;

@Component
public class CardScorer {

    public int currentStrength(CardInstance card, RowState row) {
        if (hasAbility(card, AbilityCodes.HERO)) {
            return card.card().basePower();
        }

        int strength = row.weatherActive() ? 1 : card.card().basePower();

        if (hasAbility(card, AbilityCodes.TIGHT_BOND)) {
            int sameIdCount = countSameCardIdInRow(card, row);
            if (sameIdCount >= 2) {
                strength = strength * sameIdCount;
            }
        }

        strength = strength + countOtherMoraleBoostersInRow(card, row);

        if (row.hornActive()) {
            strength = strength * 2;
        }

        return strength;
    }

    private boolean hasAbility(CardInstance ci, String code) {
        return ci.card().abilities().contains(code);
    }

    private int countSameCardIdInRow(CardInstance ci, RowState row) {
        int count = 0;
        String cardId = ci.card().id();
        for (CardInstance other : row.units()) {
            if (other.card().id().equals(cardId)) {
                count++;
            }
        }
        return count;
    }

    private int countOtherMoraleBoostersInRow(CardInstance ci, RowState row) {
        int count = 0;
        for (CardInstance other : row.units()) {
            if (other.instanceId().equals(ci.instanceId())) {
                continue;
            }
            if (hasAbility(other, AbilityCodes.HERO)) {
                continue;
            }
            if (hasAbility(other, AbilityCodes.MORALE_BOOST)) {
                count++;
            }
        }
        return count;
    }
}
