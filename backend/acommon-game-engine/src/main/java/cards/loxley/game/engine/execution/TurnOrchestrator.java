package cards.loxley.game.engine.execution;

import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveDescriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TurnOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TurnOrchestrator.class);

    private final MoveExecutor executor;
    private final RoundResolver resolver;
    private final MoveDescriber describer;

    public TurnOrchestrator(MoveExecutor executor, RoundResolver resolver, MoveDescriber describer) {
        this.executor = executor;
        this.resolver = resolver;
        this.describer = describer;
    }

    public void playTurn(GameState state, Move move) {
        if (state.matchEnded()) {
            throw new IllegalStateException("Match has already ended");
        }
        if (move.player() != state.currentTurn()) {
            throw new IllegalStateException(
                    "Not your turn: currentTurn=" + state.currentTurn()
                            + " but move was for " + move.player());
        }

        if (log.isDebugEnabled()) {
            log.debug("Turn played by {}: {}", move.player(), describer.describe(move, state));
        }

        executor.execute(state, move);

        if (state.bothPlayersPassed()) {
            resolver.resolveEndOfRound(state);
            return;
        }

        PlayerState opponent = state.opponent(move.player());
        if (!opponent.passed()) {
            state.switchTurn();
        }
    }
}
