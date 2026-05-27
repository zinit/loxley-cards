package cards.loxley.game.engine.execution;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerStateTestSupport;
import cards.loxley.game.engine.move.MoveTestFixtures;
import cards.loxley.game.engine.move.PassMove;
import cards.loxley.game.engine.move.PlayCardMove;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TurnOrchestratorTest {

    @Autowired
    TurnOrchestrator orchestrator;

    @Autowired
    GameDefinition definition;

    @Test
    void normalPlaySwitchesTurnToOpponent() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of(lady));
        state.assignTurn(Player.P1);
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        orchestrator.playTurn(state, PlayCardMove.unit(Player.P1, handId, RowId.CLOSE));

        assertThat(state.currentTurn()).isEqualTo(Player.P2);
    }

    @Test
    void whenOpponentAlreadyPassedCurrentPlayerKeepsTheTurn() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady, lady), List.of());
        state.assignTurn(Player.P1);
        PlayerStateTestSupport.markPassed(state.playerState(Player.P2));
        String handId = state.playerState(Player.P1).hand().get(0).instanceId();

        orchestrator.playTurn(state, PlayCardMove.unit(Player.P1, handId, RowId.CLOSE));

        assertThat(state.currentTurn()).isEqualTo(Player.P1);
        assertThat(state.playerState(Player.P1).passed()).isFalse();
    }

    @Test
    void bothPassResolvesEndOfRound() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        state.assignTurn(Player.P1);
        PlayerStateTestSupport.markPassed(state.playerState(Player.P2));

        orchestrator.playTurn(state, new PassMove(Player.P1));

        // Tie 0-0 → no winner this round, no rounds awarded, advance to round 2.
        assertThat(state.roundHistory()).hasSize(1);
        assertThat(state.roundNumber()).isEqualTo(2);
        assertThat(state.playerState(Player.P1).passed()).isFalse();
        assertThat(state.playerState(Player.P2).passed()).isFalse();
    }

    @Test
    void movingOutOfTurnThrows() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        state.assignTurn(Player.P1);

        assertThatThrownBy(() -> orchestrator.playTurn(state, new PassMove(Player.P2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not your turn");
    }

    @Test
    void playingAfterMatchEndedThrows() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        state.assignTurn(Player.P1);
        state.endMatchWith(Player.P1);

        assertThatThrownBy(() -> orchestrator.playTurn(state, new PassMove(Player.P1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Match has already ended");
    }
}
