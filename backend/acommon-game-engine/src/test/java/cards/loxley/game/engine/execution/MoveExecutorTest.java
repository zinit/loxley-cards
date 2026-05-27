package cards.loxley.game.engine.execution;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.domain.state.PlayerStateTestSupport;
import cards.loxley.game.engine.move.MoveTestFixtures;
import cards.loxley.game.engine.move.PassMove;
import cards.loxley.game.engine.move.PlayCardMove;
import cards.loxley.game.engine.move.UseLeaderMove;
import cards.loxley.game.engine.scoring.CardScorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MoveExecutorTest {

    @Autowired
    MoveExecutor executor;

    @Autowired
    GameDefinition definition;

    @Autowired
    CardScorer cardScorer;

    // ---------- Group A: PassMove ----------

    @Test
    void passSetsPassedAndLeavesHandIntact() {
        GameState state = MoveTestFixtures.gameStateWith(
                definition,
                List.of(MoveTestFixtures.cardById(definition, "lady_marian")),
                List.of()
        );
        PlayerState p1 = state.playerState(Player.P1);
        int handSizeBefore = p1.hand().size();

        executor.execute(state, new PassMove(Player.P1));

        assertThat(p1.passed()).isTrue();
        assertThat(p1.hand()).hasSize(handSizeBefore);
    }

    @Test
    void passAfterPassThrowsIllegalMoveException() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        PlayerStateTestSupport.markPassed(state.playerState(Player.P1));

        assertThatThrownBy(() -> executor.execute(state, new PassMove(Player.P1)))
                .isInstanceOf(IllegalMoveException.class)
                .hasMessageContaining("already passed");
    }

    // ---------- Group B: UseLeaderMove ----------

    @Test
    void useLeaderSetsLeaderUsedFlag() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());

        executor.execute(state, new UseLeaderMove(Player.P1));

        assertThat(state.playerState(Player.P1).leaderUsed()).isTrue();
    }

    @Test
    void useLeaderTwiceThrows() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        executor.execute(state, new UseLeaderMove(Player.P1));

        assertThatThrownBy(() -> executor.execute(state, new UseLeaderMove(Player.P1)))
                .isInstanceOf(IllegalMoveException.class)
                .hasMessageContaining("Leader already used");
    }

    // ---------- Group C: PlayCardMove UNIT ----------

    @Test
    void playPlainUnitMovesItFromHandToOwnRow() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        String handId = p1.hand().get(0).instanceId();

        executor.execute(state, PlayCardMove.unit(Player.P1, handId, RowId.CLOSE));

        assertThat(p1.hand()).isEmpty();
        assertThat(p1.board().close().findUnit(handId)).isPresent();
        assertThat(p1.graveyard()).noneMatch(ci -> ci.instanceId().equals(handId));
    }

    @Test
    void playSpyUnitLandsOnOpponentRowButKeepsOriginalOwner() {
        Card allan = MoveTestFixtures.cardById(definition, "allan_a_dale");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(allan), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        PlayerState p2 = state.playerState(Player.P2);
        String handId = p1.hand().get(0).instanceId();
        int p1HandBefore = p1.hand().size();
        int p2HandBefore = p2.hand().size();
        int p2DeckBefore = p2.deck().size();

        executor.execute(state, PlayCardMove.spy(Player.P1, handId, RowId.CLOSE));

        assertThat(p1.board().close().findUnit(handId)).isEmpty();
        assertThat(p2.board().close().findUnit(handId)).isPresent();
        assertThat(p2.board().close().findUnit(handId).get().owner()).isEqualTo(Player.P1);
        // SPY effect runs drawCards(2) but P1's deck is empty in this fixture,
        // so hand shrinks by the one card just played and grows by 0 drawn cards.
        assertThat(p1.hand()).hasSize(p1HandBefore - 1);
        assertThat(p2.hand()).hasSize(p2HandBefore);
        assertThat(p2.deck()).hasSize(p2DeckBefore);
    }

    @Test
    void playHeroUnitGoesToOwnBoardAndScoresBasePower() {
        Card littleJohn = MoveTestFixtures.cardById(definition, "little_john");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(littleJohn), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        String handId = p1.hand().get(0).instanceId();

        executor.execute(state, PlayCardMove.unit(Player.P1, handId, RowId.CLOSE));

        CardInstance onBoard = p1.board().close().findUnit(handId).orElseThrow();
        assertThat(cardScorer.currentStrength(onBoard, p1.board().close())).isEqualTo(10);
    }

    // ---------- Group D: PlayCardMove SPECIAL ----------

    @Test
    void playGlobalWeatherSpecialMovesToGraveyardAndAppliesWeatherSymmetrically() {
        Card icyDawn = MoveTestFixtures.cardById(definition, "icy_dawn");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(icyDawn), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        PlayerState p2 = state.playerState(Player.P2);
        String handId = p1.hand().get(0).instanceId();

        executor.execute(state, PlayCardMove.special(Player.P1, handId));

        assertThat(p1.hand()).isEmpty();
        assertThat(p1.graveyard()).anyMatch(ci -> ci.instanceId().equals(handId));
        assertThat(p1.board().close().weatherActive()).isTrue();
        assertThat(p2.board().close().weatherActive()).isTrue();
        assertThat(p1.board().ranged().weatherActive()).isFalse();
        assertThat(p1.board().siege().weatherActive()).isFalse();
    }

    @Test
    void playHornMovesToGraveyardAndAppliesHornEffectToSelectedRow() {
        Card horn = MoveTestFixtures.cardById(definition, "sherwood_horn");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(horn), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        PlayerState p2 = state.playerState(Player.P2);
        String handId = p1.hand().get(0).instanceId();

        executor.execute(state, PlayCardMove.specialOnRow(Player.P1, handId, RowId.RANGED));

        assertThat(p1.hand()).isEmpty();
        assertThat(p1.graveyard()).anyMatch(ci -> ci.instanceId().equals(handId));
        assertThat(p1.board().ranged().hornActive()).isTrue();
        assertThat(p1.board().close().hornActive()).isFalse();
        assertThat(p2.board().ranged().hornActive()).isFalse();
    }

    @Test
    void playDecoyReturnsRecycledTargetToHandWithNewInstanceId() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        CardInstance targetOnBoard = new CardInstance(lady, Player.P1);
        p1.board().close().addUnit(targetOnBoard);
        String decoyHandId = p1.hand().get(0).instanceId();
        String targetId = targetOnBoard.instanceId();

        executor.execute(state, PlayCardMove.specialOnUnit(Player.P1, decoyHandId, targetId));

        // Original physical unit is gone from the board (and its instanceId with it).
        assertThat(p1.board().close().findUnit(targetId)).isEmpty();
        // Decoy itself in graveyard.
        assertThat(p1.graveyard()).anyMatch(ci -> ci.instanceId().equals(decoyHandId));
        // Recycled card lands in P1's hand with a fresh instanceId and owner = P1.
        CardInstance recycled = p1.hand().stream()
                .filter(ci -> ci.card().id().equals("lady_marian"))
                .findFirst().orElseThrow();
        assertThat(recycled.instanceId()).isNotEqualTo(targetId);
        assertThat(recycled.owner()).isEqualTo(Player.P1);
    }

    @Test
    void decoyOnHeroThrows() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card littleJohn = MoveTestFixtures.cardById(definition, "little_john");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        CardInstance hero = new CardInstance(littleJohn, Player.P1);
        p1.board().close().addUnit(hero);
        String decoyHandId = p1.hand().get(0).instanceId();

        assertThatThrownBy(() ->
                executor.execute(state,
                        PlayCardMove.specialOnUnit(Player.P1, decoyHandId, hero.instanceId())))
                .isInstanceOf(IllegalMoveException.class)
                .hasMessageContaining("Hero");
    }

    // ---------- Group E: validation passthrough ----------

    @Test
    void playingCardNotInHandThrows() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());

        assertThatThrownBy(() ->
                executor.execute(state,
                        PlayCardMove.unit(Player.P1, "no-such-id", RowId.CLOSE)))
                .isInstanceOf(IllegalMoveException.class)
                .hasMessageContaining("Card not in hand");
    }
}
