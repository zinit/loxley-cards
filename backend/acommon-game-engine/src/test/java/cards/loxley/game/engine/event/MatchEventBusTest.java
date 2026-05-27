package cards.loxley.game.engine.event;

import cards.loxley.game.domain.state.GameState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MatchEventBusTest {

    @Test
    void publishesEventToAllListenersInRegisteredOrder() {
        List<String> log = new ArrayList<>();
        MatchEventListener first = (event, state) -> log.add("first");
        MatchEventListener second = (event, state) -> log.add("second");
        MatchEventListener third = (event, state) -> log.add("third");

        MatchEventBus bus = new MatchEventBus(List.of(first, second, third));

        bus.publish(new RoundEnded(1, 0, 0, Optional.empty()), null);

        assertThat(log).containsExactly("first", "second", "third");
    }

    @Test
    void throwingListenerDoesNotPreventLaterListenersFromRunning() {
        List<String> log = new ArrayList<>();
        MatchEventListener buggy = (event, state) -> {
            throw new RuntimeException("intentional test failure");
        };
        MatchEventListener clean = (event, state) -> log.add("clean");

        MatchEventBus bus = new MatchEventBus(List.of(buggy, clean));

        bus.publish(new RoundEnded(1, 5, 5, Optional.empty()), (GameState) null);

        assertThat(log).containsExactly("clean");
    }
}
