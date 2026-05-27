package cards.loxley.game.engine.event;

import cards.loxley.game.domain.state.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MatchEventBus {

    private static final Logger log = LoggerFactory.getLogger(MatchEventBus.class);

    private final List<MatchEventListener> listeners;

    public MatchEventBus(List<MatchEventListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    public void publish(MatchEvent event, GameState state) {
        for (MatchEventListener listener : listeners) {
            try {
                listener.onEvent(event, state);
            } catch (RuntimeException ex) {
                log.warn("Listener {} threw on event {}: {}",
                        listener.getClass().getSimpleName(), event, ex.getMessage(), ex);
            }
        }
    }
}
