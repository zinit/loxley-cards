package cards.loxley.game.engine.faction;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.event.RoundEnded;
import cards.loxley.game.engine.move.MoveTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DrawOnRoundWinListenerTest {

    @Autowired
    DrawOnRoundWinListener listener;

    @Autowired
    GameDefinition definition;

    @Test
    void sherwoodOutlawsWinnerDrawsOneCard() {
        Card filler = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = buildState("SHERWOOD_OUTLAWS", List.of(filler, filler, filler));
        PlayerState p1 = state.playerState(Player.P1);
        int handBefore = p1.hand().size();
        int deckBefore = p1.deck().size();

        listener.onEvent(new RoundEnded(1, 10, 5, Optional.of(Player.P1)), state);

        assertThat(p1.hand()).hasSize(handBefore + 1);
        assertThat(p1.deck()).hasSize(deckBefore - 1);
    }

    @Test
    void unknownFactionWinnerDoesNotDraw() {
        Card filler = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = buildState("MADE_UP_FACTION", List.of(filler, filler));
        PlayerState p1 = state.playerState(Player.P1);
        int handBefore = p1.hand().size();
        int deckBefore = p1.deck().size();

        listener.onEvent(new RoundEnded(1, 10, 5, Optional.of(Player.P1)), state);

        assertThat(p1.hand()).hasSize(handBefore);
        assertThat(p1.deck()).hasSize(deckBefore);
    }

    @Test
    void tieDoesNotTriggerDrawForEitherPlayer() {
        Card filler = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = buildState("SHERWOOD_OUTLAWS", List.of(filler, filler, filler));
        PlayerState p1 = state.playerState(Player.P1);
        PlayerState p2 = state.playerState(Player.P2);
        int p1HandBefore = p1.hand().size();
        int p2HandBefore = p2.hand().size();

        listener.onEvent(new RoundEnded(1, 5, 5, Optional.empty()), state);

        assertThat(p1.hand()).hasSize(p1HandBefore);
        assertThat(p2.hand()).hasSize(p2HandBefore);
    }

    @Test
    void winnerWithEmptyDeckDrawsNothingGracefully() {
        GameState state = buildState("SHERWOOD_OUTLAWS", List.of());
        PlayerState p1 = state.playerState(Player.P1);
        int handBefore = p1.hand().size();
        assertThat(p1.deck()).isEmpty();

        listener.onEvent(new RoundEnded(1, 10, 0, Optional.of(Player.P1)), state);

        assertThat(p1.hand()).hasSize(handBefore);
        assertThat(p1.deck()).isEmpty();
    }

    private GameState buildState(String factionId, List<Card> p1Deck) {
        Card leaderCard = MoveTestFixtures.leaderCard(definition);
        CardInstance p1Leader = new CardInstance(leaderCard, Player.P1);
        CardInstance p2Leader = new CardInstance(leaderCard, Player.P2);
        List<CardInstance> p1DeckInstances = new ArrayList<>();
        for (Card c : p1Deck) {
            p1DeckInstances.add(new CardInstance(c, Player.P1));
        }
        PlayerState p1 = new PlayerState(Player.P1, p1Leader, p1DeckInstances, factionId);
        PlayerState p2 = new PlayerState(Player.P2, p2Leader, List.of(), factionId);
        return new GameState(p1, p2, Player.P1);
    }
}
