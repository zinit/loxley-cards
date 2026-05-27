package cards.loxley.game.engine.ability.effects;

import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.move.MoveTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WeatherEffectsTest {

    @Autowired
    WeatherCloseEffect closeEffect;

    @Autowired
    WeatherRangedEffect rangedEffect;

    @Autowired
    WeatherSiegeEffect siegeEffect;

    @Autowired
    ClearWeatherEffect clearEffect;

    @Autowired
    GameDefinition definition;

    @Test
    void weatherCloseAppliesToBothPlayersCloseRowOnly() {
        GameState state = emptyState();

        closeEffect.apply(ctxFor(state));

        assertThat(state.playerState(Player.P1).board().close().weatherActive()).isTrue();
        assertThat(state.playerState(Player.P2).board().close().weatherActive()).isTrue();
        assertThat(state.playerState(Player.P1).board().ranged().weatherActive()).isFalse();
        assertThat(state.playerState(Player.P2).board().siege().weatherActive()).isFalse();
    }

    @Test
    void weatherCloseAppliedTwiceRemainsActive() {
        GameState state = emptyState();
        closeEffect.apply(ctxFor(state));
        closeEffect.apply(ctxFor(state));

        assertThat(state.playerState(Player.P1).board().close().weatherActive()).isTrue();
        assertThat(state.playerState(Player.P2).board().close().weatherActive()).isTrue();
    }

    @Test
    void weatherRangedAppliesToBothPlayersRangedRow() {
        GameState state = emptyState();

        rangedEffect.apply(ctxFor(state));

        assertThat(state.playerState(Player.P1).board().ranged().weatherActive()).isTrue();
        assertThat(state.playerState(Player.P2).board().ranged().weatherActive()).isTrue();
        assertThat(state.playerState(Player.P1).board().close().weatherActive()).isFalse();
    }

    @Test
    void weatherSiegeAppliesToBothPlayersSiegeRow() {
        GameState state = emptyState();

        siegeEffect.apply(ctxFor(state));

        assertThat(state.playerState(Player.P1).board().siege().weatherActive()).isTrue();
        assertThat(state.playerState(Player.P2).board().siege().weatherActive()).isTrue();
        assertThat(state.playerState(Player.P1).board().ranged().weatherActive()).isFalse();
    }

    @Test
    void clearWeatherClearsAllThreeRowsBothPlayers() {
        GameState state = emptyState();
        closeEffect.apply(ctxFor(state));
        rangedEffect.apply(ctxFor(state));
        siegeEffect.apply(ctxFor(state));

        clearEffect.apply(ctxFor(state));

        for (Player p : Player.values()) {
            for (RowId r : RowId.values()) {
                assertThat(state.playerState(p).board().row(r).weatherActive())
                        .as("weather still active on %s/%s", p, r)
                        .isFalse();
            }
        }
    }

    @Test
    void clearWeatherDoesNotRemoveHorns() {
        GameState state = emptyState();
        state.playerState(Player.P1).board().close().applyHorn();
        closeEffect.apply(ctxFor(state));

        clearEffect.apply(ctxFor(state));

        assertThat(state.playerState(Player.P1).board().close().weatherActive()).isFalse();
        assertThat(state.playerState(Player.P1).board().close().hornActive()).isTrue();
    }

    private GameState emptyState() {
        var leader = MoveTestFixtures.leaderCard(definition);
        var p1 = MoveTestFixtures.playerWithHand(Player.P1, leader, List.of());
        var p2 = MoveTestFixtures.playerWithHand(Player.P2, leader, List.of());
        return new GameState(p1, p2, Player.P1);
    }

    private AbilityContext ctxFor(GameState state) {
        return new AbilityContext(state, Player.P1, null, null, null);
    }
}
