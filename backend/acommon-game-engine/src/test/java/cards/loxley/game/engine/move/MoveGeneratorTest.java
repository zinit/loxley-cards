package cards.loxley.game.engine.move;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.GameStateFactory;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.domain.state.PlayerStateTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static cards.loxley.game.engine.move.MoveTestFixtures.cardById;
import static cards.loxley.game.engine.move.MoveTestFixtures.gameStateWith;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MoveGeneratorTest {

    @Autowired
    MoveGenerator generator;

    @Autowired
    MoveValidator validator;

    @Autowired
    GameStateFactory factory;

    @Autowired
    GameDefinition definition;

    // ---------- Group A: basics ----------

    @Test
    void freshGameContainsPassAndUseLeader() {
        GameState state = factory.newGame(definition.deck(), definition.deck());

        List<Move> moves = generator.legalMoves(state, Player.P1);

        assertThat(moves).anyMatch(m -> m instanceof PassMove);
        assertThat(moves).anyMatch(m -> m instanceof UseLeaderMove);
    }

    @Test
    void passedPlayerHasNoLegalMoves() {
        GameState state = factory.newGame(definition.deck(), definition.deck());
        PlayerStateTestSupport.markPassed(state.playerState(Player.P1));

        List<Move> moves = generator.legalMoves(state, Player.P1);

        assertThat(moves).isEmpty();
    }

    @Test
    void passedPlayerDoesNotAffectOpponentMoves() {
        GameState state = factory.newGame(definition.deck(), definition.deck());
        PlayerStateTestSupport.markPassed(state.playerState(Player.P1));

        List<Move> p2Moves = generator.legalMoves(state, Player.P2);

        assertThat(p2Moves).isNotEmpty();
        assertThat(p2Moves).anyMatch(m -> m instanceof PassMove);
        assertThat(p2Moves).anyMatch(m -> m instanceof UseLeaderMove);
    }

    @Test
    void leaderUsedRemovesUseLeaderMoveButKeepsRest() {
        GameState state = factory.newGame(definition.deck(), definition.deck());
        PlayerStateTestSupport.markLeaderUsed(state.playerState(Player.P1));

        List<Move> moves = generator.legalMoves(state, Player.P1);

        assertThat(moves).noneMatch(m -> m instanceof UseLeaderMove);
        assertThat(moves).anyMatch(m -> m instanceof PassMove);
    }

    // ---------- Group B: unit rows ----------

    @Test
    void plainUnitOnCloseRowGeneratesSingleUnitMoveOnClose() {
        Card ladyMarian = cardById(definition, "lady_marian");
        GameState state = gameStateWith(definition, List.of(ladyMarian), List.of());

        List<Move> moves = generator.legalMoves(state, Player.P1);
        List<PlayCardMove> playMoves = playMovesFor(moves, state, Player.P1, "lady_marian");

        assertThat(playMoves).hasSize(1);
        assertThat(playMoves.get(0).targetRow()).isEqualTo(RowId.CLOSE);
        assertThat(playMoves.get(0).targetInstanceId()).isNull();
    }

    @Test
    void plainUnitOnRangedRowGeneratesSingleUnitMoveOnRanged() {
        Card much = cardById(definition, "much_millers_son");
        GameState state = gameStateWith(definition, List.of(much), List.of());

        List<PlayCardMove> playMoves = playMovesFor(
                generator.legalMoves(state, Player.P1), state, Player.P1, "much_millers_son");

        assertThat(playMoves).hasSize(1);
        assertThat(playMoves.get(0).targetRow()).isEqualTo(RowId.RANGED);
    }

    @Test
    void spyUnitOnCloseRowGeneratesSingleSpyMoveOnClose() {
        Card allan = cardById(definition, "allan_a_dale");
        GameState state = gameStateWith(definition, List.of(allan), List.of());

        List<PlayCardMove> playMoves = playMovesFor(
                generator.legalMoves(state, Player.P1), state, Player.P1, "allan_a_dale");

        assertThat(playMoves).hasSize(1);
        assertThat(playMoves.get(0).targetRow()).isEqualTo(RowId.CLOSE);
        assertThat(playMoves.get(0).targetInstanceId()).isNull();
    }

    @Test
    void spyUnitOnSiegeRowGeneratesSingleSpyMoveOnSiege() {
        Card hugo = cardById(definition, "hugo_rascal");
        GameState state = gameStateWith(definition, List.of(hugo), List.of());

        List<PlayCardMove> playMoves = playMovesFor(
                generator.legalMoves(state, Player.P1), state, Player.P1, "hugo_rascal");

        assertThat(playMoves).hasSize(1);
        assertThat(playMoves.get(0).targetRow()).isEqualTo(RowId.SIEGE);
    }

    // ---------- Group C: specials ----------

    @Test
    void globalSpecialGeneratesSingleSpecialMove() {
        Card icyDawn = cardById(definition, "icy_dawn");
        GameState state = gameStateWith(definition, List.of(icyDawn), List.of());

        List<PlayCardMove> playMoves = playMovesFor(
                generator.legalMoves(state, Player.P1), state, Player.P1, "icy_dawn");

        assertThat(playMoves).hasSize(1);
        assertThat(playMoves.get(0).targetRow()).isNull();
        assertThat(playMoves.get(0).targetInstanceId()).isNull();
    }

    @Test
    void hornGeneratesThreeRowVariants() {
        Card horn = cardById(definition, "sherwood_horn");
        GameState state = gameStateWith(definition, List.of(horn), List.of());

        List<PlayCardMove> playMoves = playMovesFor(
                generator.legalMoves(state, Player.P1), state, Player.P1, "sherwood_horn");

        assertThat(playMoves).hasSize(3);
        assertThat(playMoves).extracting(PlayCardMove::targetRow)
                .containsExactlyInAnyOrder(RowId.CLOSE, RowId.RANGED, RowId.SIEGE);
        assertThat(playMoves).allMatch(m -> m.targetInstanceId() == null);
    }

    @Test
    void decoyOnEmptyBoardGeneratesZeroMoves() {
        Card decoy = cardById(definition, "scarecrow");
        GameState state = gameStateWith(definition, List.of(decoy), List.of());

        List<PlayCardMove> playMoves = playMovesFor(
                generator.legalMoves(state, Player.P1), state, Player.P1, "scarecrow");

        assertThat(playMoves).isEmpty();
    }

    @Test
    void decoyWithThreeNonHeroUnitsGeneratesThreeMoves() {
        Card decoy = cardById(definition, "scarecrow");
        Card ladyMarian = cardById(definition, "lady_marian");
        GameState state = gameStateWith(definition, List.of(decoy), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        p1.board().close().addUnit(new CardInstance(ladyMarian, Player.P1));
        p1.board().close().addUnit(new CardInstance(ladyMarian, Player.P1));
        p1.board().close().addUnit(new CardInstance(ladyMarian, Player.P1));

        List<PlayCardMove> playMoves = playMovesFor(
                generator.legalMoves(state, Player.P1), state, Player.P1, "scarecrow");

        assertThat(playMoves).hasSize(3);
        assertThat(playMoves).allMatch(m -> m.targetInstanceId() != null);
        assertThat(playMoves).allMatch(m -> m.targetRow() == null);
    }

    @Test
    void decoyWithTwoNonHeroAndOneHeroGeneratesTwoMoves() {
        Card decoy = cardById(definition, "scarecrow");
        Card ladyMarian = cardById(definition, "lady_marian");
        Card littleJohn = cardById(definition, "little_john");
        GameState state = gameStateWith(definition, List.of(decoy), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        p1.board().close().addUnit(new CardInstance(ladyMarian, Player.P1));
        p1.board().close().addUnit(new CardInstance(ladyMarian, Player.P1));
        p1.board().close().addUnit(new CardInstance(littleJohn, Player.P1));

        List<PlayCardMove> playMoves = playMovesFor(
                generator.legalMoves(state, Player.P1), state, Player.P1, "scarecrow");

        assertThat(playMoves).hasSize(2);
    }

    // ---------- Group D: integration ----------

    @Test
    void mixedHandProducesExpectedTotalMoveCount() {
        Card ladyMarian = cardById(definition, "lady_marian");     // unit CLOSE
        Card allan = cardById(definition, "allan_a_dale");         // spy CLOSE
        Card horn = cardById(definition, "sherwood_horn");         // SELECTED_ROW
        Card icyDawn = cardById(definition, "icy_dawn");           // GLOBAL
        Card littleJohn = cardById(definition, "little_john");     // hero unit CLOSE
        GameState state = gameStateWith(
                definition,
                List.of(ladyMarian, allan, horn, icyDawn, littleJohn),
                List.of()
        );

        List<Move> moves = generator.legalMoves(state, Player.P1);

        // Pass(1) + Leader(1) + unit(1) + spy(1) + horn(3) + weather(1) + hero(1) = 9
        assertThat(moves).hasSize(9);
    }

    @Test
    void afterLeaderUsedFlagIsSetUseLeaderMoveIsAbsent() {
        Card ladyMarian = cardById(definition, "lady_marian");
        GameState state = gameStateWith(definition, List.of(ladyMarian), List.of());
        PlayerStateTestSupport.markLeaderUsed(state.playerState(Player.P1));

        List<Move> moves = generator.legalMoves(state, Player.P1);

        assertThat(moves).noneMatch(m -> m instanceof UseLeaderMove);
        assertThat(moves).anyMatch(m -> m instanceof PassMove);
        assertThat(moves).anyMatch(m -> m instanceof PlayCardMove);
    }

    // ---------- Consistency with validator ----------

    @Test
    void sampledGeneratedMovesAreAllValid() {
        GameState state = factory.newGame(definition.deck(), definition.deck());
        List<Move> moves = generator.legalMoves(state, Player.P1);

        assertThat(moves).isNotEmpty();
        List<Move> shuffled = new java.util.ArrayList<>(moves);
        Collections.shuffle(shuffled, new Random(42));
        int sample = Math.min(10, shuffled.size());

        for (int i = 0; i < sample; i++) {
            Move m = shuffled.get(i);
            ValidationResult result = validator.validate(state, m);
            assertThat(result)
                    .as("generated move should be valid: %s", m)
                    .isInstanceOf(ValidationResult.Valid.class);
        }
    }

    private List<PlayCardMove> playMovesFor(List<Move> moves, GameState state, Player player, String cardId) {
        PlayerState ps = state.playerState(player);
        return moves.stream()
                .filter(m -> m instanceof PlayCardMove)
                .map(m -> (PlayCardMove) m)
                .filter(m -> ps.hand().stream()
                        .anyMatch(ci -> ci.instanceId().equals(m.handInstanceId())
                                && ci.card().id().equals(cardId)))
                .toList();
    }
}
