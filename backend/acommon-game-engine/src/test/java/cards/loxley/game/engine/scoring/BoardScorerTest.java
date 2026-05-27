package cards.loxley.game.engine.scoring;

import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.BoardSide;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.GameStateFactory;
import cards.loxley.game.domain.state.Player;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static cards.loxley.game.engine.scoring.CardFixtures.instance;
import static cards.loxley.game.engine.scoring.CardFixtures.plain;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BoardScorerTest {

    @Autowired
    BoardScorer boardScorer;

    @Autowired
    RowScorer rowScorer;

    @Autowired
    GameStateFactory factory;

    @Autowired
    GameDefinition definition;

    @Test
    void sideStrengthSumsAllThreeRows() {
        BoardSide side = new BoardSide();
        side.row(RowId.CLOSE).addUnit(instance(plain("c", 5)));
        side.row(RowId.RANGED).addUnit(instance(plain("r", 6)));
        side.row(RowId.SIEGE).addUnit(instance(plain("s", 7)));

        assertThat(boardScorer.sideStrength(side)).isEqualTo(18);
    }

    @Test
    void freshGamePlayerStrengthIsZero() {
        GameState state = factory.newGame(definition.deck(), definition.deck());

        assertThat(boardScorer.playerStrength(state, Player.P1)).isZero();
        assertThat(boardScorer.playerStrength(state, Player.P2)).isZero();
    }

    @Test
    void weatherOnCloseDoesNotAffectOtherRows() {
        BoardSide side = new BoardSide();
        side.row(RowId.CLOSE).addUnit(instance(plain("c", 5)));
        side.row(RowId.RANGED).addUnit(instance(plain("r", 6)));
        side.row(RowId.SIEGE).addUnit(instance(plain("s", 7)));

        side.row(RowId.CLOSE).applyWeather();

        assertThat(rowScorer.rowStrength(side.row(RowId.CLOSE))).isEqualTo(1);
        assertThat(rowScorer.rowStrength(side.row(RowId.RANGED))).isEqualTo(6);
        assertThat(rowScorer.rowStrength(side.row(RowId.SIEGE))).isEqualTo(7);
        assertThat(boardScorer.sideStrength(side)).isEqualTo(1 + 6 + 7);
    }
}
