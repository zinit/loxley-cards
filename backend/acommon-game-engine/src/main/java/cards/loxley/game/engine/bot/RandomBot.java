package cards.loxley.game.engine.bot;

import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.move.Move;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public class RandomBot implements BotStrategy {

    private final Random rng;

    public RandomBot(Random rng) {
        this.rng = Objects.requireNonNull(rng, "rng");
    }

    public RandomBot(long seed) {
        this(new Random(seed));
    }

    @Override
    public String name() {
        return "random";
    }

    @Override
    public Move chooseMove(GameState state, Player player, List<Move> legalMoves) {
        if (legalMoves.isEmpty()) {
            throw new IllegalStateException("Cannot choose a move from an empty list");
        }
        return legalMoves.get(rng.nextInt(legalMoves.size()));
    }
}
