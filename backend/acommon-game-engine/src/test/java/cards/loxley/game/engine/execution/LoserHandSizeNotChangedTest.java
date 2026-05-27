package cards.loxley.game.engine.execution;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.move.MoveTestFixtures;
import cards.loxley.game.engine.move.PassMove;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LoserHandSizeNotChangedTest {

    @Autowired
    TurnOrchestrator orchestrator;

    @Autowired
    GameDefinition definition;

    @Test
    void loserHandSizeNotChangedByRoundEndedEvent() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card leader = MoveTestFixtures.leaderCard(definition);

        // P1 already has 1 round won (i.e., we're heading into the match-ending round).
        // R3 setup: P1 has 5 cards on hand, P2 has 5 cards on hand, P2 will lose 26-17 style.
        PlayerState p1 = MoveTestFixtures.playerWithHandAndDeck(
                Player.P1, leader,
                List.of(lady, lady, lady, lady, lady),
                List.of(lady, lady, lady));
        PlayerState p2 = MoveTestFixtures.playerWithHandAndDeck(
                Player.P2, leader,
                List.of(lady, lady, lady, lady, lady),
                List.of(lady, lady, lady));
        // Pre-load round 1 win for P1 so the R-currently-being-played win pushes match to end.
        p1.incrementRoundsWon();
        // Force a clearly-winning board for P1: 4 Lady Marians on CLOSE = 20.
        // P2 board: 2 Lady Marians = 10. P1 wins this round 20-10.
        for (int i = 0; i < 4; i++) {
            p1.board().close().addUnit(new CardInstance(lady, Player.P1));
        }
        for (int i = 0; i < 2; i++) {
            p2.board().close().addUnit(new CardInstance(lady, Player.P2));
        }
        // Both players pass to trigger the round-end resolver via the orchestrator.
        // Set currentTurn to P1; P1 passes first, then P2 passes -> resolveEndOfRound.
        GameState state = new GameState(p1, p2, Player.P1);

        int loserHandBefore = p2.hand().size();
        int loserDeckBefore = p2.deck().size();
        int winnerHandBefore = p1.hand().size();
        int winnerDeckBefore = p1.deck().size();

        assertThat(state.matchEnded()).isFalse();
        assertThat(state.bothPlayersPassed()).isFalse();

        orchestrator.playTurn(state, new PassMove(Player.P1));
        assertThat(state.matchEnded()).isFalse();
        assertThat(state.bothPlayersPassed()).isFalse();
        // Switch turn to P2.
        assertThat(state.currentTurn()).isEqualTo(Player.P2);

        orchestrator.playTurn(state, new PassMove(Player.P2));
        // Both passed → resolveEndOfRound → P1 wins → roundsWon goes to 2 → match ends.
        assertThat(state.matchEnded()).isTrue();
        assertThat(state.matchWinner()).hasValue(Player.P1);

        // Loser (P2) hand and deck size MUST be unchanged across the bothPassed → matchEnded transition.
        assertThat(p2.hand().size())
                .as("loser hand size unchanged")
                .isEqualTo(loserHandBefore);
        assertThat(p2.deck().size())
                .as("loser deck size unchanged")
                .isEqualTo(loserDeckBefore);

        // Sanity: winner drew exactly 1 card via faction passive (hand +1, deck -1).
        assertThat(p1.hand().size()).isEqualTo(winnerHandBefore + 1);
        assertThat(p1.deck().size()).isEqualTo(winnerDeckBefore - 1);
    }
}
