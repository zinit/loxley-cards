package cards.loxley.game.engine.event;

import cards.loxley.game.domain.state.Player;

import java.util.Optional;

public record MatchEnded(
        Optional<Player> winner,
        int totalRounds
) implements MatchEvent {
}
