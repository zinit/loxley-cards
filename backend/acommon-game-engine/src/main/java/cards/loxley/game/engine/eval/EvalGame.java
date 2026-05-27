package cards.loxley.game.engine.eval;

import cards.loxley.game.domain.state.Player;

import java.util.Optional;

public record EvalGame(
        long seed,
        Optional<Player> winner,
        int p1RoundsWon,
        int p2RoundsWon,
        int totalTurns
) {
}
