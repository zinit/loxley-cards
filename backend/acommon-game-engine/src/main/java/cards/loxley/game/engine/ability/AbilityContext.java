package cards.loxley.game.engine.ability;

import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.move.PlayCardMove;

public record AbilityContext(
        GameState state,
        Player player,
        CardInstance sourceCard,
        PlayCardMove move,
        AbilityRegistry registry
) {

    public PlayerState selfState() {
        return state.playerState(player);
    }

    public PlayerState opponentState() {
        return state.opponent(player);
    }
}
