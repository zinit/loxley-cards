package cards.loxley.game.engine.move;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MoveDescriberTest {

    @Autowired
    MoveDescriber describer;

    @Autowired
    GameDefinition definition;

    @Test
    void describesPassMove() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        String out = describer.describe(new PassMove(Player.P1), state);
        assertThat(out).isEqualTo("PASS");
    }

    @Test
    void describesUseLeaderMoveWithLeaderName() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        String out = describer.describe(new UseLeaderMove(Player.P1), state);
        assertThat(out).startsWith("USE_LEADER (");
        assertThat(out).endsWith(")");
        assertThat(out).contains(state.playerState(Player.P1).leader().card().name());
    }

    @Test
    void describesUnitPlayWithRowOnly() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());
        CardInstance hand = state.playerState(Player.P1).hand().get(0);

        String out = describer.describe(
                PlayCardMove.unit(Player.P1, hand.instanceId(), RowId.CLOSE), state);

        assertThat(out).isEqualTo("PLAY Lady Marian on CLOSE");
    }

    @Test
    void describesDecoyPlayWithTargetNameRowAndUnitIndex() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card trebuchet = MoveTestFixtures.cardById(definition, "sherwood_trebuchet");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        CardInstance ladyUnit = new CardInstance(lady, Player.P1);
        CardInstance trebuchetUnit = new CardInstance(trebuchet, Player.P1);
        state.playerState(Player.P1).board().close().addUnit(ladyUnit);
        state.playerState(Player.P1).board().siege().addUnit(trebuchetUnit);

        CardInstance decoyHand = state.playerState(Player.P1).hand().get(0);
        String out = describer.describe(
                PlayCardMove.specialOnUnit(Player.P1, decoyHand.instanceId(), trebuchetUnit.instanceId()),
                state);

        assertThat(out).isEqualTo("PLAY Strach na Wróble on Trebusz Sherwoodu (SIEGE) [u2]");
    }
}
