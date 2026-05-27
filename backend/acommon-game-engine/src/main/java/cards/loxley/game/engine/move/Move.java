package cards.loxley.game.engine.move;

import cards.loxley.game.domain.state.Player;

public sealed interface Move permits PlayCardMove, PassMove, UseLeaderMove {
    Player player();
}
