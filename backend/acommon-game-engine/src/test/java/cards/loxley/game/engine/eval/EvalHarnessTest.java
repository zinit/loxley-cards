package cards.loxley.game.engine.eval;

import cards.loxley.game.engine.bot.HeuristicMediumBot;
import cards.loxley.game.engine.bot.RandomBot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EvalHarnessTest {

    @Autowired
    EvalHarness harness;

    @Autowired
    HeuristicMediumBot mediumBot;

    @Test
    void runMatchesReturnsResultsWhoseCountsSumToGameCount() {
        EvalResult result = harness.runMatches(
                new RandomBot(11L), new RandomBot(22L), 5, 1L);

        assertThat(result.gameCount()).isEqualTo(5);
        assertThat(result.bot1Wins() + result.bot2Wins() + result.draws()).isEqualTo(5);
        assertThat(result.games()).hasSize(5);
    }

    @Test
    void differentSeedBasesCanProduceDifferentResults() {
        RandomBot bot1 = new RandomBot(11L);
        RandomBot bot2 = new RandomBot(22L);

        EvalResult resultA = harness.runMatches(bot1, bot2, 5, 1L);
        EvalResult resultB = harness.runMatches(new RandomBot(11L), new RandomBot(22L), 5, 500L);

        // Seeds differ so at least one game's seed differs and the per-game record differs.
        assertThat(resultA.games().get(0).seed()).isNotEqualTo(resultB.games().get(0).seed());
    }

    @Test
    void mediumBotOutperformsRandomBotOverTwentyGames() {
        EvalResult result = harness.runMatches(
                mediumBot, new RandomBot(99L), 20, 1000L);

        assertThat(result.bot1Wins())
                .as("medium win count over 20 games: %s", result.summary())
                .isGreaterThan(result.gameCount() / 2);
    }
}
