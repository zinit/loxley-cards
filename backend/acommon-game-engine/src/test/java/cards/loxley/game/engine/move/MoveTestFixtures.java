package cards.loxley.game.engine.move;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;

import java.util.ArrayList;
import java.util.List;

public final class MoveTestFixtures {

    private MoveTestFixtures() {
    }

    public static Card cardById(GameDefinition def, String id) {
        return def.cards().stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Card not found: " + id));
    }

    public static Card leaderCard(GameDefinition def) {
        return def.cards().stream()
                .filter(c -> c.cardType() == CardType.LEADER)
                .findFirst()
                .orElseThrow();
    }

    public static PlayerState playerWithHand(Player player, Card leaderCard, List<Card> handCards) {
        return playerWithHandAndDeck(player, leaderCard, handCards, List.of());
    }

    public static PlayerState playerWithHandAndDeck(
            Player player,
            Card leaderCard,
            List<Card> handCards,
            List<Card> deckCards
    ) {
        CardInstance leader = new CardInstance(leaderCard, player);
        List<CardInstance> initialDeck = new ArrayList<>();
        for (Card c : deckCards) {
            initialDeck.add(new CardInstance(c, player));
        }
        // Hand cards reversed so drawCards() pops them in the order the caller listed them.
        for (int i = handCards.size() - 1; i >= 0; i--) {
            initialDeck.add(new CardInstance(handCards.get(i), player));
        }
        PlayerState ps = new PlayerState(player, leader, initialDeck);
        ps.drawCards(handCards.size());
        return ps;
    }

    public static GameState gameStateWith(GameDefinition def, List<Card> p1Hand, List<Card> p2Hand) {
        Card leader = leaderCard(def);
        PlayerState p1 = playerWithHand(Player.P1, leader, p1Hand);
        PlayerState p2 = playerWithHand(Player.P2, leader, p2Hand);
        return new GameState(p1, p2, Player.P1);
    }
}
