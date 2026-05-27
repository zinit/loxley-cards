package cards.loxley.game.engine.eval;

import java.util.List;

public record EvalResult(
        String bot1Name,
        String bot2Name,
        int gameCount,
        int bot1Wins,
        int bot2Wins,
        int draws,
        List<EvalGame> games
) {

    public double bot1WinRate() {
        return gameCount == 0 ? 0.0 : (double) bot1Wins / gameCount;
    }

    public double bot2WinRate() {
        return gameCount == 0 ? 0.0 : (double) bot2Wins / gameCount;
    }

    public double drawRate() {
        return gameCount == 0 ? 0.0 : (double) draws / gameCount;
    }

    public String summary() {
        return String.format(
                "%s vs %s (%d games): bot1=%d/%d (%.0f%%) bot2=%d/%d (%.0f%%) draws=%d/%d (%.0f%%)",
                bot1Name, bot2Name, gameCount,
                bot1Wins, gameCount, bot1WinRate() * 100,
                bot2Wins, gameCount, bot2WinRate() * 100,
                draws, gameCount, drawRate() * 100
        );
    }
}
