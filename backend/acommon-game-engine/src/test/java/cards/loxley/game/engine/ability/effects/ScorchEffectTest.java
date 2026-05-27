package cards.loxley.game.engine.ability.effects;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.move.MoveTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class ScorchEffectTest {

    @Autowired
    ScorchEffect effect;

    @Autowired
    GameDefinition definition;

    @Test
    void emptyBoardScorchIsNoOp() {
        GameState state = emptyState();

        assertThatCode(() -> effect.apply(ctxFor(state))).doesNotThrowAnyException();
    }

    @Test
    void boardWithOnlyHeroesIsUntouched() {
        GameState state = emptyState();
        Card littleJohn = MoveTestFixtures.cardById(definition, "little_john");
        CardInstance hero = new CardInstance(littleJohn, Player.P1);
        state.playerState(Player.P1).board().close().addUnit(hero);

        effect.apply(ctxFor(state));

        assertThat(state.playerState(Player.P1).board().close().findUnit(hero.instanceId()))
                .isPresent();
        assertThat(state.playerState(Player.P1).graveyard()).isEmpty();
    }

    @Test
    void scorchRemovesAllNonHeroUnitsTiedForMaxStrengthLeavingHero() {
        GameState state = emptyState();
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers"); // bp 4
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");           // bp 5
        Card richard = MoveTestFixtures.cardById(definition, "sir_richard_lea");    // bp 5
        Card littleJohn = MoveTestFixtures.cardById(definition, "little_john");     // bp 10 hero

        CardInstance brothersP1 = new CardInstance(brothers, Player.P1);
        CardInstance ladyP1 = new CardInstance(lady, Player.P1);
        CardInstance heroP2 = new CardInstance(littleJohn, Player.P2);
        CardInstance richardP2 = new CardInstance(richard, Player.P2);
        state.playerState(Player.P1).board().close().addUnit(brothersP1);
        state.playerState(Player.P1).board().close().addUnit(ladyP1);
        state.playerState(Player.P2).board().close().addUnit(heroP2);
        state.playerState(Player.P2).board().close().addUnit(richardP2);

        effect.apply(ctxFor(state));

        // Hero survives.
        assertThat(state.playerState(Player.P2).board().close().findUnit(heroP2.instanceId()))
                .isPresent();
        // Both 5-strength non-Heroes burn.
        assertThat(state.playerState(Player.P1).board().close().findUnit(ladyP1.instanceId()))
                .isEmpty();
        assertThat(state.playerState(Player.P2).board().close().findUnit(richardP2.instanceId()))
                .isEmpty();
        // Weaker 4-strength card survives.
        assertThat(state.playerState(Player.P1).board().close().findUnit(brothersP1.instanceId()))
                .isPresent();

        assertThat(state.playerState(Player.P1).graveyard())
                .extracting(CardInstance::instanceId)
                .containsExactly(ladyP1.instanceId());
        assertThat(state.playerState(Player.P2).graveyard())
                .extracting(CardInstance::instanceId)
                .containsExactly(richardP2.instanceId());
    }

    @Test
    void scorchUsesCurrentStrengthIncludingTightBondModifier() {
        GameState state = emptyState();
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers"); // bp 4, TIGHT_BOND
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");           // bp 5
        CardInstance b1 = new CardInstance(brothers, Player.P1);
        CardInstance b2 = new CardInstance(brothers, Player.P1);
        CardInstance b3 = new CardInstance(brothers, Player.P1);
        CardInstance ladyP2 = new CardInstance(lady, Player.P2);
        state.playerState(Player.P1).board().close().addUnit(b1);
        state.playerState(Player.P1).board().close().addUnit(b2);
        state.playerState(Player.P1).board().close().addUnit(b3);
        state.playerState(Player.P2).board().close().addUnit(ladyP2);
        // Each Bracia: 4 * 3 = 12 (max); Lady: 5.

        effect.apply(ctxFor(state));

        assertThat(state.playerState(Player.P1).board().close().unitCount()).isZero();
        assertThat(state.playerState(Player.P2).board().close().findUnit(ladyP2.instanceId()))
                .isPresent();
        assertThat(state.playerState(Player.P1).graveyard())
                .extracting(CardInstance::instanceId)
                .containsExactlyInAnyOrder(
                        b1.instanceId(), b2.instanceId(), b3.instanceId());
    }

    @Test
    void scorchedCardGoesToOwnerGraveyardEvenWhenOnOpponentRow() {
        GameState state = emptyState();
        Card allan = MoveTestFixtures.cardById(definition, "allan_a_dale");
        CardInstance p1SpyOnP2Side = new CardInstance(allan, Player.P1);
        state.playerState(Player.P2).board().close().addUnit(p1SpyOnP2Side);

        effect.apply(ctxFor(state));

        assertThat(state.playerState(Player.P2).board().close().findUnit(p1SpyOnP2Side.instanceId()))
                .isEmpty();
        assertThat(state.playerState(Player.P1).graveyard())
                .extracting(CardInstance::instanceId)
                .containsExactly(p1SpyOnP2Side.instanceId());
        assertThat(state.playerState(Player.P2).graveyard()).isEmpty();
    }

    private GameState emptyState() {
        Card leader = MoveTestFixtures.leaderCard(definition);
        return new GameState(
                MoveTestFixtures.playerWithHand(Player.P1, leader, List.of()),
                MoveTestFixtures.playerWithHand(Player.P2, leader, List.of()),
                Player.P1
        );
    }

    private AbilityContext ctxFor(GameState state) {
        return new AbilityContext(state, Player.P1, null, null, null);
    }
}
