package cards.loxley.cli;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveGenerator;
import cards.loxley.cli.MoveTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CliMovesCommandTest {

    @Autowired
    MovesListFormatter formatter;

    @Autowired
    MoveGenerator generator;

    @Autowired
    GameDefinition definition;

    @Test
    void twoDecoysAndFourUnitsCollapseToFourDedupedLines() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card richard = MoveTestFixtures.cardById(definition, "sir_richard_lea");
        Card trebuchet = MoveTestFixtures.cardById(definition, "sherwood_trebuchet");
        Card engineer = MoveTestFixtures.cardById(definition, "sherwood_engineer");
        // Hand: 2× Decoy.
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy, decoy), List.of());
        // Board: 4 non-hero units.
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(richard, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P1).board().siege().addUnit(new CardInstance(trebuchet, Player.P1));
        state.playerState(Player.P1).board().siege().addUnit(new CardInstance(engineer, Player.P1));

        List<Move> legal = generator.legalMoves(state, Player.P1);
        List<String> lines = formatter.format(legal, state);

        List<String> decoyLines = lines.stream()
                .filter(l -> l.contains("Strach na Wróble"))
                .toList();

        assertThat(decoyLines).hasSize(4);
        assertThat(decoyLines).allMatch(l -> l.contains("(×2 copies in hand)"));
    }

    @Test
    void singleHornInHandShowsThreeRowEntriesWithoutCountSuffix() {
        Card horn = MoveTestFixtures.cardById(definition, "sherwood_horn");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(horn), List.of());

        List<Move> legal = generator.legalMoves(state, Player.P1);
        List<String> lines = formatter.format(legal, state);

        List<String> hornLines = lines.stream()
                .filter(l -> l.contains("Róg Sherwoodu"))
                .toList();

        assertThat(hornLines).hasSize(3);
        assertThat(hornLines).noneMatch(l -> l.contains("copies in hand"));
    }
}
