package cards.loxley.game.engine.bot;

import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.move.Move;

import java.util.List;

public interface BotStrategy {

    String name();

    Move chooseMove(GameState state, Player player, List<Move> legalMoves);
}
