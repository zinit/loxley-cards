package cards.loxley.game.engine.ability.effects;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.move.MoveTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SpyEffectTest {

    @Autowired
    SpyEffect effect;

    @Autowired
    GameDefinition definition;

    @Test
    void spyDrawsTwoCardsWhenDeckHasAtLeastTwo() {
        Card filler = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = stateWithP1DeckOf(List.of(filler, filler, filler));
        PlayerState p1 = state.playerState(Player.P1);
        int handBefore = p1.hand().size();
        int deckBefore = p1.deck().size();

        effect.apply(new AbilityContext(state, Player.P1, null, null, null));

        assertThat(p1.hand()).hasSize(handBefore + 2);
        assertThat(p1.deck()).hasSize(deckBefore - 2);
    }

    @Test
    void spyDrawsOnlyOneWhenDeckHasOneCard() {
        Card filler = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = stateWithP1DeckOf(List.of(filler));
        PlayerState p1 = state.playerState(Player.P1);
        int handBefore = p1.hand().size();

        effect.apply(new AbilityContext(state, Player.P1, null, null, null));

        assertThat(p1.hand()).hasSize(handBefore + 1);
        assertThat(p1.deck()).isEmpty();
    }

    @Test
    void spyOnEmptyDeckDrawsNothing() {
        GameState state = stateWithP1DeckOf(List.of());
        PlayerState p1 = state.playerState(Player.P1);
        int handBefore = p1.hand().size();

        effect.apply(new AbilityContext(state, Player.P1, null, null, null));

        assertThat(p1.hand()).hasSize(handBefore);
        assertThat(p1.deck()).isEmpty();
    }

    private GameState stateWithP1DeckOf(List<Card> deckCards) {
        Card leader = MoveTestFixtures.leaderCard(definition);
        PlayerState p1 = MoveTestFixtures.playerWithHandAndDeck(
                Player.P1, leader, List.of(), deckCards);
        PlayerState p2 = MoveTestFixtures.playerWithHand(Player.P2, leader, List.of());
        return new GameState(p1, p2, Player.P1);
    }
}
