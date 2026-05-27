package cards.loxley.cli;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.cli.MoveTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PlayerBoardIndexTest {

    @Autowired
    GameDefinition definition;

    @Test
    void emptyBoardProducesEmptyIndex() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());

        PlayerBoardIndex index = PlayerBoardIndex.forPlayer(state, Player.P1);

        assertThat(index.size()).isZero();
        assertThat(index.units()).isEmpty();
    }

    @Test
    void twoUnitsInCloseAreIndexedInInsertionOrder() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card richard = MoveTestFixtures.cardById(definition, "sir_richard_lea");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        CardInstance first = new CardInstance(lady, Player.P1);
        CardInstance second = new CardInstance(richard, Player.P1);
        state.playerState(Player.P1).board().close().addUnit(first);
        state.playerState(Player.P1).board().close().addUnit(second);

        PlayerBoardIndex index = PlayerBoardIndex.forPlayer(state, Player.P1);

        assertThat(index.size()).isEqualTo(2);
        assertThat(index.units().get(0).index()).isEqualTo(1);
        assertThat(index.units().get(0).unit()).isSameAs(first);
        assertThat(index.units().get(0).rowId()).isEqualTo(RowId.CLOSE);
        assertThat(index.units().get(1).index()).isEqualTo(2);
        assertThat(index.units().get(1).unit()).isSameAs(second);
    }

    @Test
    void unitsAcrossRowsAreOrderedCloseRangedSiege() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card much = MoveTestFixtures.cardById(definition, "much_millers_son");
        Card trebuchet = MoveTestFixtures.cardById(definition, "sherwood_trebuchet");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        CardInstance siegeUnit = new CardInstance(trebuchet, Player.P1);
        CardInstance rangedUnit = new CardInstance(much, Player.P1);
        CardInstance closeUnit = new CardInstance(lady, Player.P1);
        // Add SIEGE first to prove iteration order independent of insertion order across rows.
        state.playerState(Player.P1).board().siege().addUnit(siegeUnit);
        state.playerState(Player.P1).board().ranged().addUnit(rangedUnit);
        state.playerState(Player.P1).board().close().addUnit(closeUnit);

        PlayerBoardIndex index = PlayerBoardIndex.forPlayer(state, Player.P1);

        assertThat(index.units()).hasSize(3);
        assertThat(index.units().get(0).rowId()).isEqualTo(RowId.CLOSE);
        assertThat(index.units().get(0).unit()).isSameAs(closeUnit);
        assertThat(index.units().get(1).rowId()).isEqualTo(RowId.RANGED);
        assertThat(index.units().get(1).unit()).isSameAs(rangedUnit);
        assertThat(index.units().get(2).rowId()).isEqualTo(RowId.SIEGE);
        assertThat(index.units().get(2).unit()).isSameAs(siegeUnit);
    }

    @Test
    void spyOnOpponentBoardIsIncludedFilteredByOwner() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card allan = MoveTestFixtures.cardById(definition, "allan_a_dale");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        CardInstance ladyOwn = new CardInstance(lady, Player.P1);
        // Spy: P1 owns it physically on P2 board (after spy play).
        CardInstance spyOnOpponent = new CardInstance(allan, Player.P1);
        // Bot's own unit on its own board should NOT appear in P1 index.
        CardInstance botUnit = new CardInstance(lady, Player.P2);
        state.playerState(Player.P1).board().close().addUnit(ladyOwn);
        state.playerState(Player.P2).board().close().addUnit(spyOnOpponent);
        state.playerState(Player.P2).board().ranged().addUnit(botUnit);

        PlayerBoardIndex index = PlayerBoardIndex.forPlayer(state, Player.P1);

        assertThat(index.units()).hasSize(2);
        assertThat(index.units().get(0).unit()).isSameAs(ladyOwn);
        assertThat(index.units().get(0).index()).isEqualTo(1);
        assertThat(index.units().get(1).unit()).isSameAs(spyOnOpponent);
        assertThat(index.units().get(1).index()).isEqualTo(2);
        assertThat(index.units().get(1).rowId()).isEqualTo(RowId.CLOSE);
    }

    @Test
    void findByIndexAndInstanceIdLookupsBehaveAsExpected() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card richard = MoveTestFixtures.cardById(definition, "sir_richard_lea");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        CardInstance first = new CardInstance(lady, Player.P1);
        CardInstance second = new CardInstance(richard, Player.P1);
        state.playerState(Player.P1).board().close().addUnit(first);
        state.playerState(Player.P1).board().close().addUnit(second);

        PlayerBoardIndex index = PlayerBoardIndex.forPlayer(state, Player.P1);

        assertThat(index.find(1)).hasValueSatisfying(u -> assertThat(u.unit()).isSameAs(first));
        assertThat(index.find(2)).hasValueSatisfying(u -> assertThat(u.unit()).isSameAs(second));
        assertThat(index.find(0)).isEmpty();
        assertThat(index.find(3)).isEmpty();
        assertThat(index.findByInstanceId(second.instanceId()))
                .hasValueSatisfying(u -> assertThat(u.index()).isEqualTo(2));
        assertThat(index.findByInstanceId("nope")).isEmpty();
    }
}
