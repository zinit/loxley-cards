package cards.loxley.game.loader;

import cards.loxley.game.domain.card.Ability;
import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.Deck;
import cards.loxley.game.domain.card.DeckEntry;
import cards.loxley.game.domain.card.DeckSummary;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.Row;
import cards.loxley.game.domain.card.RowId;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class GameDefinitionValidator {

    public void validate(GameDefinition def) {
        Set<RowId> rowIds = new HashSet<>();
        for (Row row : def.rows()) {
            rowIds.add(row.id());
        }

        Set<String> abilityCodes = new HashSet<>();
        for (Ability ability : def.abilities()) {
            abilityCodes.add(ability.code());
        }

        Map<String, Card> cardsById = new HashMap<>();
        for (Card card : def.cards()) {
            cardsById.put(card.id(), card);
        }

        for (Card card : def.cards()) {
            if (card.row() != null && !rowIds.contains(card.row())) {
                throw new GameDefinitionValidationException(
                        "Card '" + card.id() + "' references unknown row '" + card.row() + "'");
            }
            for (String abilityCode : card.abilities()) {
                if (!abilityCodes.contains(abilityCode)) {
                    throw new GameDefinitionValidationException(
                            "Card '" + card.id() + "' references unknown ability '" + abilityCode + "'");
                }
            }
        }

        Deck deck = def.deck();

        Card leader = cardsById.get(deck.leaderCardId());
        if (leader == null) {
            throw new GameDefinitionValidationException(
                    "Deck leaderCardId '" + deck.leaderCardId() + "' does not exist in cards");
        }
        if (leader.cardType() != CardType.LEADER) {
            throw new GameDefinitionValidationException(
                    "Deck leaderCardId '" + leader.id() + "' must have cardType LEADER but was " + leader.cardType());
        }

        int unitCopies = 0;
        int specialCopies = 0;
        for (DeckEntry entry : deck.cards()) {
            Card card = cardsById.get(entry.cardId());
            if (card == null) {
                throw new GameDefinitionValidationException(
                        "Deck entry references unknown card '" + entry.cardId() + "'");
            }
            switch (card.cardType()) {
                case UNIT -> unitCopies += entry.count();
                case SPECIAL -> specialCopies += entry.count();
                case LEADER -> throw new GameDefinitionValidationException(
                        "Deck entry '" + entry.cardId() + "' is a LEADER card and must not appear in deck.cards");
            }
        }

        DeckSummary summary = deck.summary();
        if (summary.leaderCount() != 1) {
            throw new GameDefinitionValidationException(
                    "Deck summary.leaderCount expected 1 but was " + summary.leaderCount());
        }
        if (summary.unitCardCopies() != unitCopies) {
            throw new GameDefinitionValidationException(
                    "Deck summary.unitCardCopies expected " + unitCopies + " but was " + summary.unitCardCopies());
        }
        if (summary.specialCardCopies() != specialCopies) {
            throw new GameDefinitionValidationException(
                    "Deck summary.specialCardCopies expected " + specialCopies + " but was " + summary.specialCardCopies());
        }
        int totalExpected = unitCopies + specialCopies;
        if (summary.totalPlayableCardCopies() != totalExpected) {
            throw new GameDefinitionValidationException(
                    "Deck summary.totalPlayableCardCopies expected " + totalExpected
                            + " but was " + summary.totalPlayableCardCopies());
        }
    }
}
