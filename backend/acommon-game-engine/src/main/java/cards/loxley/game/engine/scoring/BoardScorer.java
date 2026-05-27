package cards.loxley.game.engine.scoring;

import cards.loxley.game.domain.state.BoardSide;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import org.springframework.stereotype.Component;

@Component
public class BoardScorer {

    private final RowScorer rowScorer;

    public BoardScorer(RowScorer rowScorer) {
        this.rowScorer = rowScorer;
    }

    public int sideStrength(BoardSide side) {
        return rowScorer.rowStrength(side.close())
                + rowScorer.rowStrength(side.ranged())
                + rowScorer.rowStrength(side.siege());
    }

    public int playerStrength(GameState state, Player player) {
        return sideStrength(state.playerState(player).board());
    }
}
