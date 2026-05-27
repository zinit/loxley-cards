package cards.loxley.game.engine.ability.effects;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.ability.AbilityRegistry;
import cards.loxley.game.engine.move.MoveTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class MedicEffectTest {

    @Autowired
    MedicEffect effect;

    @Autowired
    AbilityRegistry registry;

    @Autowired
    GameDefinition definition;

    @Test
    void medicOnEmptyGraveyardIsNoOp() {
        GameState state = emptyState();

        assertThatCode(() -> effect.apply(ctxFor(state))).doesNotThrowAnyException();
        assertThat(state.playerState(Player.P1).board().close().unitCount()).isZero();
    }

    @Test
    void medicRevivesSingleNonHeroUnitFromGraveyardToItsRow() {
        GameState state = emptyState();
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian"); // bp 5, CLOSE
        CardInstance corpse = new CardInstance(lady, Player.P1);
        state.playerState(Player.P1).sendToGraveyard(corpse);

        effect.apply(ctxFor(state));

        assertThat(state.playerState(Player.P1).graveyard()).isEmpty();
        assertThat(state.playerState(Player.P1).board().close().findUnit(corpse.instanceId()))
                .isPresent();
    }

    @Test
    void medicSkipsHeroCardsInGraveyard() {
        GameState state = emptyState();
        Card littleJohn = MoveTestFixtures.cardById(definition, "little_john"); // Hero
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        CardInstance heroCorpse = new CardInstance(littleJohn, Player.P1);
        CardInstance ladyCorpse = new CardInstance(lady, Player.P1);
        state.playerState(Player.P1).sendToGraveyard(heroCorpse);
        state.playerState(Player.P1).sendToGraveyard(ladyCorpse);

        effect.apply(ctxFor(state));

        assertThat(state.playerState(Player.P1).graveyard())
                .extracting(CardInstance::instanceId)
                .containsExactly(heroCorpse.instanceId());
        assertThat(state.playerState(Player.P1).board().close().findUnit(ladyCorpse.instanceId()))
                .isPresent();
    }

    @Test
    void medicPicksStrongestNonHeroFromGraveyard() {
        GameState state = emptyState();
        Card weakUnit = MoveTestFixtures.cardById(definition, "peasants_of_locksley");   // bp 1
        Card midUnit = MoveTestFixtures.cardById(definition, "sherwood_brothers");        // bp 4
        Card strongUnit = MoveTestFixtures.cardById(definition, "lady_marian");           // bp 5
        CardInstance weak = new CardInstance(weakUnit, Player.P1);
        CardInstance mid = new CardInstance(midUnit, Player.P1);
        CardInstance strong = new CardInstance(strongUnit, Player.P1);
        PlayerState p1 = state.playerState(Player.P1);
        p1.sendToGraveyard(weak);
        p1.sendToGraveyard(mid);
        p1.sendToGraveyard(strong);

        effect.apply(ctxFor(state));

        // Strongest (Lady Marian, bp 5, CLOSE) revived.
        assertThat(p1.board().close().findUnit(strong.instanceId())).isPresent();
        assertThat(p1.graveyard())
                .extracting(CardInstance::instanceId)
                .containsExactlyInAnyOrder(weak.instanceId(), mid.instanceId());
    }

    @Test
    void medicChainRevivesAllAvailableUnitsRecursively() {
        GameState state = emptyState();
        Card friarTuck = MoveTestFixtures.cardById(definition, "friar_tuck"); // UNIT bp 5, MEDIC, SIEGE
        Card bracia = MoveTestFixtures.cardById(definition, "sherwood_brothers"); // bp 4 CLOSE
        CardInstance medicA = new CardInstance(friarTuck, Player.P1);
        CardInstance medicB = new CardInstance(friarTuck, Player.P1);
        CardInstance plainUnit = new CardInstance(bracia, Player.P1);
        PlayerState p1 = state.playerState(Player.P1);
        p1.sendToGraveyard(medicA);
        p1.sendToGraveyard(medicB);
        p1.sendToGraveyard(plainUnit);

        effect.apply(ctxFor(state));

        // All three cards revived (chain Medic -> Medic -> UNIT).
        assertThat(p1.graveyard()).isEmpty();
        assertThat(p1.board().row(RowId.SIEGE).unitCount()).isEqualTo(2);
        assertThat(p1.board().row(RowId.CLOSE).unitCount()).isEqualTo(1);
        assertThat(p1.board().row(RowId.CLOSE).findUnit(plainUnit.instanceId())).isPresent();
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
        return new AbilityContext(state, Player.P1, null, null, registry);
    }
}
