package cards.loxley.cli;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.PlayerState;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HandRenderer {

    public String render(PlayerState player) {
        StringBuilder sb = new StringBuilder("YOUR HAND:").append(System.lineSeparator());
        List<CardInstance> hand = player.hand();
        if (hand.isEmpty()) {
            sb.append("  (empty)").append(System.lineSeparator());
            return sb.toString();
        }
        for (int i = 0; i < hand.size(); i++) {
            sb.append(String.format("  [%d] %s%n", i + 1, describeCard(hand.get(i).card())));
        }
        return sb.toString();
    }

    private String describeCard(Card card) {
        String name = padRight(card.name(), 26);
        if (card.cardType() == CardType.SPECIAL) {
            String target = switch (card.playTarget()) {
                case SELECTED_ROW -> "ROW";
                case OWN_UNIT_ON_BOARD -> "UNIT";
                default -> "GLOBAL";
            };
            String abilityList = describeAbilities(card);
            return String.format("%s special target:%-7s ability:%s", name, target, abilityList);
        }
        String row = card.row() == null ? "—" : card.row().name();
        String abilityList = describeAbilities(card);
        return String.format("%s pow:%-3d row:%-7s ability:%s",
                name, card.basePower(), row, abilityList);
    }

    private String describeAbilities(Card card) {
        if (card.abilities().isEmpty()) {
            return "(none)";
        }
        return String.join(",", card.abilities());
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        return s + " ".repeat(width - s.length());
    }
}
