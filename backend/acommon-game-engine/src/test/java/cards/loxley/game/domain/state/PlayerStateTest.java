package cards.loxley.game.domain.state;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.MvpImportance;
import cards.loxley.game.domain.card.PlayTarget;
import cards.loxley.game.domain.card.RowId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerStateTest {

    private static final Card LEADER_CARD = new Card(
            "leader_test", "Leader", CardType.LEADER, "SHERWOOD_OUTLAWS",
            "desc", null, null, List.of(),
            PlayTarget.GLOBAL, "leader", MvpImportance.REQUIRED
    );

    private static final Card UNIT_CARD = new Card(
            "unit_test", "Unit", CardType.UNIT, "SHERWOOD_OUTLAWS",
            "desc", RowId.CLOSE, 4, List.of(),
            PlayTarget.OWN_BOARD, "basic_points", MvpImportance.OPTIONAL
    );

    private PlayerState newStateWithDeckOf(int size) {
        CardInstance leader = new CardInstance(LEADER_CARD, Player.P1);
        List<CardInstance> deck = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            deck.add(new CardInstance(UNIT_CARD, Player.P1));
        }
        return new PlayerState(Player.P1, leader, deck);
    }

    @Test
    void drawCardFromNonEmptyDeckMovesOneCardToHand() {
        PlayerState state = newStateWithDeckOf(5);
        int initialDeckSize = state.deck().size();
        int initialHandSize = state.hand().size();

        Optional<CardInstance> drawn = state.drawCard();

        assertThat(drawn).isPresent();
        assertThat(state.deck()).hasSize(initialDeckSize - 1);
        assertThat(state.hand()).hasSize(initialHandSize + 1);
        assertThat(state.hand()).contains(drawn.get());
    }

    @Test
    void drawCardFromEmptyDeckReturnsEmpty() {
        PlayerState state = newStateWithDeckOf(0);

        Optional<CardInstance> drawn = state.drawCard();

        assertThat(drawn).isEmpty();
        assertThat(state.hand()).isEmpty();
    }

    @Test
    void drawCardsReturnsActualDrawnCountWhenDeckRunsOut() {
        PlayerState state = newStateWithDeckOf(2);

        int drawn = state.drawCards(3);

        assertThat(drawn).isEqualTo(2);
        assertThat(state.deck()).isEmpty();
        assertThat(state.hand()).hasSize(2);
    }

    @Test
    void removeFromHandRemovesMatchingInstance() {
        PlayerState state = newStateWithDeckOf(3);
        state.drawCards(3);
        CardInstance target = state.hand().get(1);
        int handSizeBefore = state.hand().size();

        boolean removed = state.removeFromHand(target.instanceId());

        assertThat(removed).isTrue();
        assertThat(state.hand()).hasSize(handSizeBefore - 1);
        assertThat(state.hand()).doesNotContain(target);
    }

    @Test
    void sendToGraveyardAppendsToGraveyardList() {
        PlayerState state = newStateWithDeckOf(0);
        CardInstance ci1 = new CardInstance(UNIT_CARD, Player.P1);
        CardInstance ci2 = new CardInstance(UNIT_CARD, Player.P1);

        state.sendToGraveyard(ci1);
        state.sendToGraveyard(ci2);

        assertThat(state.graveyard()).containsExactly(ci1, ci2);
    }
}
