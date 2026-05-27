package cards.loxley.game.engine.execution;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.domain.state.PlayerStateTestSupport;
import cards.loxley.game.engine.move.MoveTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RoundResolverTest {

    @Autowired
    RoundResolver resolver;

    @Autowired
    GameDefinition definition;

    // ---------- Group A: scoring and round outcomes ----------

    @Test
    void higherScoreP1WinsRoundAndIncrementsRoundsWon() {
        GameState state = emptyState();
        addTwoBraciaCloseTo(state, Player.P1);     // 2 * 4 * 2 = 16 (tight bond)
        addLadyMarianCloseTo(state, Player.P2);    // 5
        passBoth(state);

        resolver.resolveEndOfRound(state);

        assertThat(state.playerState(Player.P1).roundsWon()).isEqualTo(1);
        assertThat(state.playerState(Player.P2).roundsWon()).isZero();
        assertThat(state.roundHistory()).hasSize(1);
        assertThat(state.roundHistory().get(0).winner()).contains(Player.P1);
        assertThat(state.roundHistory().get(0).p1Score()).isEqualTo(16);
        assertThat(state.roundHistory().get(0).p2Score()).isEqualTo(5);
    }

    @Test
    void higherScoreP2WinsRound() {
        GameState state = emptyState();
        addLadyMarianCloseTo(state, Player.P1);    // 5
        addTwoBraciaCloseTo(state, Player.P2);     // 16
        passBoth(state);

        resolver.resolveEndOfRound(state);

        assertThat(state.playerState(Player.P2).roundsWon()).isEqualTo(1);
        assertThat(state.playerState(Player.P1).roundsWon()).isZero();
        assertThat(state.roundHistory().get(0).winner()).contains(Player.P2);
    }

    @Test
    void tiedScoreLeavesRoundsWonUntouchedAndRecordsEmptyWinner() {
        GameState state = emptyState();
        addLadyMarianCloseTo(state, Player.P1);    // 5
        addLadyMarianCloseTo(state, Player.P2);    // 5
        passBoth(state);

        resolver.resolveEndOfRound(state);

        assertThat(state.playerState(Player.P1).roundsWon()).isZero();
        assertThat(state.playerState(Player.P2).roundsWon()).isZero();
        assertThat(state.roundHistory().get(0).winner()).isEmpty();
    }

    @Test
    void resolvingWithoutBothPassedThrows() {
        GameState state = emptyState();
        PlayerStateTestSupport.markPassed(state.playerState(Player.P1));

        assertThatThrownBy(() -> resolver.resolveEndOfRound(state))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both players");
    }

    // ---------- Group B: match end ----------

    @Test
    void p1WinsTwoRoundsStraightEndsMatch() {
        GameState state = emptyState();

        addTwoBraciaCloseTo(state, Player.P1);
        addLadyMarianCloseTo(state, Player.P2);
        passBoth(state);
        resolver.resolveEndOfRound(state);

        assertThat(state.matchEnded()).isFalse();
        assertThat(state.roundNumber()).isEqualTo(2);

        addTwoBraciaCloseTo(state, Player.P1);
        addLadyMarianCloseTo(state, Player.P2);
        passBoth(state);
        resolver.resolveEndOfRound(state);

        assertThat(state.matchEnded()).isTrue();
        assertThat(state.matchWinner()).contains(Player.P1);
        assertThat(state.roundHistory()).hasSize(2);
    }

    @Test
    void splitRoundsThenP1WinsThirdRoundEndsMatchInFavorOfP1() {
        GameState state = emptyState();

        addTwoBraciaCloseTo(state, Player.P1);
        addLadyMarianCloseTo(state, Player.P2);
        passBoth(state);
        resolver.resolveEndOfRound(state);

        addLadyMarianCloseTo(state, Player.P1);
        addTwoBraciaCloseTo(state, Player.P2);
        passBoth(state);
        resolver.resolveEndOfRound(state);

        assertThat(state.matchEnded()).isFalse();
        assertThat(state.roundNumber()).isEqualTo(3);

        addTwoBraciaCloseTo(state, Player.P1);
        addLadyMarianCloseTo(state, Player.P2);
        passBoth(state);
        resolver.resolveEndOfRound(state);

        assertThat(state.matchEnded()).isTrue();
        assertThat(state.matchWinner()).contains(Player.P1);
        assertThat(state.playerState(Player.P1).roundsWon()).isEqualTo(2);
        assertThat(state.playerState(Player.P2).roundsWon()).isEqualTo(1);
    }

    @Test
    void splitRoundsThenTieInThirdEndsMatchAsDraw() {
        GameState state = emptyState();

        addTwoBraciaCloseTo(state, Player.P1);
        addLadyMarianCloseTo(state, Player.P2);
        passBoth(state);
        resolver.resolveEndOfRound(state);

        addLadyMarianCloseTo(state, Player.P1);
        addTwoBraciaCloseTo(state, Player.P2);
        passBoth(state);
        resolver.resolveEndOfRound(state);

        addLadyMarianCloseTo(state, Player.P1);
        addLadyMarianCloseTo(state, Player.P2);
        passBoth(state);
        resolver.resolveEndOfRound(state);

        assertThat(state.matchEnded()).isTrue();
        assertThat(state.matchWinner()).isEmpty();
        assertThat(state.playerState(Player.P1).roundsWon()).isEqualTo(1);
        assertThat(state.playerState(Player.P2).roundsWon()).isEqualTo(1);
        assertThat(state.roundHistory()).hasSize(3);
    }

    @Test
    void afterRoundWinBoardsAreClearedAndLoserStartsNextRound() {
        GameState state = emptyState();
        addTwoBraciaCloseTo(state, Player.P1);
        addLadyMarianCloseTo(state, Player.P2);
        passBoth(state);

        resolver.resolveEndOfRound(state);

        assertThat(state.roundNumber()).isEqualTo(2);
        assertThat(state.playerState(Player.P1).board().close().unitCount()).isZero();
        assertThat(state.playerState(Player.P2).board().close().unitCount()).isZero();
        assertThat(state.playerState(Player.P1).passed()).isFalse();
        assertThat(state.playerState(Player.P2).passed()).isFalse();
        // P1 won, so P2 (loser) starts next round.
        assertThat(state.currentTurn()).isEqualTo(Player.P2);
    }

    // ---------- Helpers ----------

    private GameState emptyState() {
        Card leader = MoveTestFixtures.leaderCard(definition);
        PlayerState p1 = MoveTestFixtures.playerWithHand(Player.P1, leader, List.of());
        PlayerState p2 = MoveTestFixtures.playerWithHand(Player.P2, leader, List.of());
        return new GameState(p1, p2, Player.P1);
    }

    private void addTwoBraciaCloseTo(GameState state, Player player) {
        Card bracia = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        state.playerState(player).board().close().addUnit(new CardInstance(bracia, player));
        state.playerState(player).board().close().addUnit(new CardInstance(bracia, player));
    }

    private void addLadyMarianCloseTo(GameState state, Player player) {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        state.playerState(player).board().close().addUnit(new CardInstance(lady, player));
    }

    private void passBoth(GameState state) {
        PlayerStateTestSupport.markPassed(state.playerState(Player.P1));
        PlayerStateTestSupport.markPassed(state.playerState(Player.P2));
    }
}
