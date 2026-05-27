package cards.loxley.game.engine.bot;

import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.PassMove;
import cards.loxley.game.engine.move.UseLeaderMove;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RandomBotTest {

    @Test
    void alwaysChoosesAMoveFromTheProvidedList() {
        List<Move> moves = List.of(
                new PassMove(Player.P1),
                new UseLeaderMove(Player.P1),
                new PassMove(Player.P2)
        );
        RandomBot bot = new RandomBot(42L);

        for (int i = 0; i < 20; i++) {
            Move chosen = bot.chooseMove(null, Player.P1, moves);
            assertThat(moves).contains(chosen);
        }
    }

    @Test
    void sameSeedProducesIdenticalMoveSequence() {
        List<Move> moves = List.of(
                new PassMove(Player.P1),
                new UseLeaderMove(Player.P1),
                new PassMove(Player.P2)
        );
        RandomBot botA = new RandomBot(123L);
        RandomBot botB = new RandomBot(123L);

        for (int i = 0; i < 50; i++) {
            assertThat(botA.chooseMove(null, Player.P1, moves))
                    .isSameAs(botB.chooseMove(null, Player.P1, moves));
        }
    }
}
