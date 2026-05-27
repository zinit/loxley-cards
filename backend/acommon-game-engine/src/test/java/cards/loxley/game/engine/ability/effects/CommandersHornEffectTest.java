package cards.loxley.game.engine.ability.effects;

import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.move.MoveTestFixtures;
import cards.loxley.game.engine.move.PlayCardMove;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CommandersHornEffectTest {

    @Autowired
    CommandersHornEffect effect;

    @Autowired
    GameDefinition definition;

    @Test
    void hornAffectsOnlySelectedRowOfPlayingPlayer() {
        GameState state = emptyState();

        effect.apply(ctxForRow(state, RowId.CLOSE));

        assertThat(state.playerState(Player.P1).board().close().hornActive()).isTrue();
        assertThat(state.playerState(Player.P1).board().ranged().hornActive()).isFalse();
        assertThat(state.playerState(Player.P1).board().siege().hornActive()).isFalse();
        assertThat(state.playerState(Player.P2).board().close().hornActive()).isFalse();
    }

    @Test
    void hornAppliedTwiceRemainsActive() {
        GameState state = emptyState();

        effect.apply(ctxForRow(state, RowId.SIEGE));
        effect.apply(ctxForRow(state, RowId.SIEGE));

        assertThat(state.playerState(Player.P1).board().siege().hornActive()).isTrue();
    }

    private GameState emptyState() {
        var leader = MoveTestFixtures.leaderCard(definition);
        var p1 = MoveTestFixtures.playerWithHand(Player.P1, leader, List.of());
        var p2 = MoveTestFixtures.playerWithHand(Player.P2, leader, List.of());
        return new GameState(p1, p2, Player.P1);
    }

    private AbilityContext ctxForRow(GameState state, RowId row) {
        PlayCardMove move = PlayCardMove.specialOnRow(Player.P1, "stub-hand-id", row);
        return new AbilityContext(state, Player.P1, null, move, null);
    }
}
