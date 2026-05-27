package cards.loxley.game.engine.faction;

import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.event.MatchEvent;
import cards.loxley.game.engine.event.MatchEventListener;
import cards.loxley.game.engine.event.RoundEnded;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DrawOnRoundWinListener implements MatchEventListener {

    private final FactionPassiveRegistry registry;

    public DrawOnRoundWinListener(FactionPassiveRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onEvent(MatchEvent event, GameState state) {
        if (!(event instanceof RoundEnded round)) {
            return;
        }
        Optional<Player> winner = round.winner();
        if (winner.isEmpty()) {
            return;
        }

        PlayerState winnerState = state.playerState(winner.get());
        Optional<FactionPassive> passive = registry.findByFaction(winnerState.factionId());
        if (passive.isEmpty()) {
            return;
        }
        if (!FactionPassive.DRAW_CARDS_ON_ROUND_WIN.equals(passive.get().type())) {
            return;
        }

        Object countObj = passive.get().params().get("count");
        int count = countObj instanceof Number n ? n.intValue() : 0;
        if (count <= 0) {
            return;
        }

        winnerState.drawCards(count);
    }
}
