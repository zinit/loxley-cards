package cards.loxley.game.engine.move;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.domain.state.PlayerStateTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static cards.loxley.game.engine.move.MoveTestFixtures.cardById;
import static cards.loxley.game.engine.move.MoveTestFixtures.gameStateWith;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MoveValidatorTest {

    @Autowired
    MoveValidator validator;

    @Autowired
    GameDefinition definition;

    // ---------- Happy paths ----------

    @Test
    void passIsValidForFreshPlayer() {
        GameState state = gameStateWith(definition, List.of(), List.of());

        ValidationResult result = validator.validate(state, new PassMove(Player.P1));

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void useLeaderIsValidForFreshPlayer() {
        GameState state = gameStateWith(definition, List.of(), List.of());

        ValidationResult result = validator.validate(state, new UseLeaderMove(Player.P1));

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void playUnitOnItsRowIsValid() {
        Card ladyMarian = cardById(definition, "lady_marian");
        GameState state = gameStateWith(definition, List.of(ladyMarian), List.of());
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.unit(Player.P1, handId, RowId.CLOSE));

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void playGlobalSpecialWithoutTargetsIsValid() {
        Card icyDawn = cardById(definition, "icy_dawn");
        GameState state = gameStateWith(definition, List.of(icyDawn), List.of());
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.special(Player.P1, handId));

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void playHornOnSelectedRowIsValid() {
        Card horn = cardById(definition, "sherwood_horn");
        GameState state = gameStateWith(definition, List.of(horn), List.of());
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.specialOnRow(Player.P1, handId, RowId.CLOSE));

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void playDecoyOnOwnNonHeroUnitIsValid() {
        Card decoy = cardById(definition, "scarecrow");
        Card ladyMarian = cardById(definition, "lady_marian");
        GameState state = gameStateWith(definition, List.of(decoy), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        CardInstance unitOnBoard = new CardInstance(ladyMarian, Player.P1);
        p1.board().close().addUnit(unitOnBoard);
        String handId = p1.hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.specialOnUnit(Player.P1, handId, unitOnBoard.instanceId()));

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void playSpyIsValidOnItsOwnCardRow() {
        Card hugo = cardById(definition, "hugo_rascal");
        GameState state = gameStateWith(definition, List.of(hugo), List.of());
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.spy(Player.P1, handId, RowId.SIEGE));

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
    }

    // ---------- Sad paths ----------

    @Test
    void passAfterPassIsInvalid() {
        GameState state = gameStateWith(definition, List.of(), List.of());
        PlayerStateTestSupport.markPassed(state.playerState(Player.P1));

        ValidationResult result = validator.validate(state, new PassMove(Player.P1));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason()).contains("already passed");
    }

    @Test
    void useLeaderAfterLeaderUsedIsInvalid() {
        GameState state = gameStateWith(definition, List.of(), List.of());
        PlayerStateTestSupport.markLeaderUsed(state.playerState(Player.P1));

        ValidationResult result = validator.validate(state, new UseLeaderMove(Player.P1));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason()).contains("Leader already used");
    }

    @Test
    void playCardWithUnknownHandInstanceIdIsInvalid() {
        GameState state = gameStateWith(definition, List.of(), List.of());

        ValidationResult result = validator.validate(
                state, PlayCardMove.unit(Player.P1, "non-existent-id", RowId.CLOSE));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason()).contains("Card not in hand");
    }

    @Test
    void playUnitOnWrongRowIsInvalid() {
        Card ladyMarian = cardById(definition, "lady_marian");
        GameState state = gameStateWith(definition, List.of(ladyMarian), List.of());
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.unit(Player.P1, handId, RowId.SIEGE));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason()).contains("expected CLOSE");
    }

    @Test
    void playGlobalSpecialWithTargetRowIsInvalid() {
        Card icyDawn = cardById(definition, "icy_dawn");
        GameState state = gameStateWith(definition, List.of(icyDawn), List.of());
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, new PlayCardMove(Player.P1, handId, RowId.CLOSE, null));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason()).contains("does not accept a target row");
    }

    @Test
    void playSelectedRowSpecialWithoutTargetRowIsInvalid() {
        Card horn = cardById(definition, "sherwood_horn");
        GameState state = gameStateWith(definition, List.of(horn), List.of());
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.special(Player.P1, handId));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason()).contains("requires a target row");
    }

    @Test
    void playDecoyWithoutTargetInstanceIsInvalid() {
        Card decoy = cardById(definition, "scarecrow");
        GameState state = gameStateWith(definition, List.of(decoy), List.of());
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.special(Player.P1, handId));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason()).contains("requires a target unit");
    }

    @Test
    void playDecoyOnHeroIsInvalid() {
        Card decoy = cardById(definition, "scarecrow");
        Card littleJohn = cardById(definition, "little_john");
        GameState state = gameStateWith(definition, List.of(decoy), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        CardInstance hero = new CardInstance(littleJohn, Player.P1);
        p1.board().close().addUnit(hero);
        String handId = p1.hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.specialOnUnit(Player.P1, handId, hero.instanceId()));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason()).contains("Hero");
    }

    @Test
    void playDecoyOnOpponentUnitOnOpponentSideIsInvalid() {
        Card decoy = cardById(definition, "scarecrow");
        Card ladyMarian = cardById(definition, "lady_marian");
        GameState state = gameStateWith(definition, List.of(decoy), List.of());
        PlayerState p2 = state.playerState(Player.P2);
        // Opponent's regular unit on the opponent's own board → out of reach for decoy.
        CardInstance opponentUnit = new CardInstance(ladyMarian, Player.P2);
        p2.board().close().addUnit(opponentUnit);
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.specialOnUnit(Player.P1, handId, opponentUnit.instanceId()));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason())
                .contains("Decoy can only target cards on your side of the board");
    }

    @Test
    void playDecoyOnOpponentSpyPhysicallyOnMyBoardIsValid() {
        Card decoy = cardById(definition, "scarecrow");
        Card hugo = cardById(definition, "hugo_rascal");
        GameState state = gameStateWith(definition, List.of(decoy), List.of());
        CardInstance opponentSpyOnMyBoard = new CardInstance(hugo, Player.P2);
        state.playerState(Player.P1).board().siege().addUnit(opponentSpyOnMyBoard);
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        ValidationResult result = validator.validate(
                state, PlayCardMove.specialOnUnit(Player.P1, handId, opponentSpyOnMyBoard.instanceId()));

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void playCardAfterPassIsInvalid() {
        Card ladyMarian = cardById(definition, "lady_marian");
        GameState state = gameStateWith(definition, List.of(ladyMarian), List.of());
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();
        PlayerStateTestSupport.markPassed(state.playerState(Player.P1));

        ValidationResult result = validator.validate(
                state, PlayCardMove.unit(Player.P1, handId, RowId.CLOSE));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason()).contains("already passed");
    }
}
