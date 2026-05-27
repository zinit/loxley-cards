package cards.loxley.game.engine.event;

import cards.loxley.game.domain.state.GameState;

public interface MatchEventListener {

    void onEvent(MatchEvent event, GameState state);
}
