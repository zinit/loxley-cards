package cards.loxley.game.engine.event;

import cards.loxley.game.domain.state.Player;

import java.util.Optional;

public record RoundEnded(
        int roundNumber,
        int p1Score,
        int p2Score,
        Optional<Player> winner
) implements MatchEvent {
}
