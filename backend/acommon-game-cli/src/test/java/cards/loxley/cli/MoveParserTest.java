package cards.loxley.cli;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.cli.MoveTestFixtures;
import cards.loxley.game.engine.move.PassMove;
import cards.loxley.game.engine.move.PlayCardMove;
import cards.loxley.game.engine.move.UseLeaderMove;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MoveParserTest {

    @Autowired
    MoveParser parser;

    @Autowired
    GameDefinition definition;

    @Test
    void parsesPassCommand() {
        GameState state = emptyState();
        ParseResult result = parser.parse("pass", state, Player.P1);
        assertThat(result).isInstanceOf(ParseResult.ParseSuccess.class);
        assertThat(((ParseResult.ParseSuccess) result).move()).isInstanceOf(PassMove.class);
    }

    @Test
    void parsesLeaderCommand() {
        GameState state = emptyState();
        ParseResult result = parser.parse("leader", state, Player.P1);
        assertThat(result).isInstanceOf(ParseResult.ParseSuccess.class);
        assertThat(((ParseResult.ParseSuccess) result).move()).isInstanceOf(UseLeaderMove.class);
    }

    @Test
    void parsesPlayUnitWithoutTarget() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());

        ParseResult result = parser.parse("play 1", state, Player.P1);

        assertThat(result).isInstanceOf(ParseResult.ParseSuccess.class);
        PlayCardMove move = (PlayCardMove) ((ParseResult.ParseSuccess) result).move();
        assertThat(move.targetRow()).isEqualTo(RowId.CLOSE);
        assertThat(move.targetInstanceId()).isNull();
    }

    @Test
    void parsesPlayHornWithRowTarget() {
        Card horn = MoveTestFixtures.cardById(definition, "sherwood_horn");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(horn), List.of());

        ParseResult result = parser.parse("play 1 close", state, Player.P1);

        assertThat(result).isInstanceOf(ParseResult.ParseSuccess.class);
        PlayCardMove move = (PlayCardMove) ((ParseResult.ParseSuccess) result).move();
        assertThat(move.targetRow()).isEqualTo(RowId.CLOSE);
    }

    @Test
    void parsesPlayDecoyWithUnitTarget() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        CardInstance unitOnBoard = new CardInstance(lady, Player.P1);
        state.playerState(Player.P1).board().close().addUnit(unitOnBoard);

        ParseResult result = parser.parse("play 1 unit-1", state, Player.P1);

        assertThat(result).isInstanceOf(ParseResult.ParseSuccess.class);
        PlayCardMove move = (PlayCardMove) ((ParseResult.ParseSuccess) result).move();
        assertThat(move.targetInstanceId()).isEqualTo(unitOnBoard.instanceId());
        assertThat(move.targetRow()).isNull();
    }

    @Test
    void playWithCardIndexBeyondHandReturnsError() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());

        ParseResult result = parser.parse("play 99", state, Player.P1);

        assertThat(result).isInstanceOf(ParseResult.ParseError.class);
        assertThat(((ParseResult.ParseError) result).message()).contains("No card #99");
    }

    @Test
    void playOnHornWithoutRowReturnsHelpfulError() {
        Card horn = MoveTestFixtures.cardById(definition, "sherwood_horn");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(horn), List.of());

        ParseResult result = parser.parse("play 1", state, Player.P1);

        assertThat(result).isInstanceOf(ParseResult.ParseError.class);
        assertThat(((ParseResult.ParseError) result).message()).contains("requires a row");
    }

    @Test
    void playUnitOnUnknownRowReturnsError() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());

        ParseResult result = parser.parse("play 1 nonsense", state, Player.P1);

        assertThat(result).isInstanceOf(ParseResult.ParseError.class);
        assertThat(((ParseResult.ParseError) result).message()).contains("Unknown row");
    }

    @Test
    void helpCommandIsRecognized() {
        ParseResult result = parser.parse("help", emptyState(), Player.P1);
        assertThat(result).isInstanceOf(ParseResult.ParseCommand.class);
        assertThat(((ParseResult.ParseCommand) result).commandType())
                .isEqualTo(ParseResult.ParseCommand.SHOW_HELP);
    }

    @Test
    void quitCommandIsRecognized() {
        ParseResult result = parser.parse("quit", emptyState(), Player.P1);
        assertThat(result).isInstanceOf(ParseResult.ParseCommand.class);
        assertThat(((ParseResult.ParseCommand) result).commandType())
                .isEqualTo(ParseResult.ParseCommand.QUIT);
    }

    @Test
    void emptyInputReturnsError() {
        ParseResult result = parser.parse("", emptyState(), Player.P1);
        assertThat(result).isInstanceOf(ParseResult.ParseError.class);
    }

    @Test
    void unknownCommandReturnsError() {
        ParseResult result = parser.parse("fizzbuzz", emptyState(), Player.P1);
        assertThat(result).isInstanceOf(ParseResult.ParseError.class);
        assertThat(((ParseResult.ParseError) result).message()).contains("Unknown command");
    }

    @Test
    void decoyWithUnitOutOfRangeReturnsErrorWithSize() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card richard = MoveTestFixtures.cardById(definition, "sir_richard_lea");
        Card trebuchet = MoveTestFixtures.cardById(definition, "sherwood_trebuchet");
        Card engineer = MoveTestFixtures.cardById(definition, "sherwood_engineer");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(richard, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P1).board().siege().addUnit(new CardInstance(trebuchet, Player.P1));
        state.playerState(Player.P1).board().siege().addUnit(new CardInstance(engineer, Player.P1));

        ParseResult result = parser.parse("play 1 unit-99", state, Player.P1);

        assertThat(result).isInstanceOf(ParseResult.ParseError.class);
        String msg = ((ParseResult.ParseError) result).message();
        assertThat(msg).contains("No unit at position 99");
        assertThat(msg).contains("4 unit(s)");
    }

    @Test
    void decoyWithValidUnitNumberResolvesToCorrectInstanceId() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card richard = MoveTestFixtures.cardById(definition, "sir_richard_lea");
        Card trebuchet = MoveTestFixtures.cardById(definition, "sherwood_trebuchet");
        Card engineer = MoveTestFixtures.cardById(definition, "sherwood_engineer");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        CardInstance richardOnBoard = new CardInstance(richard, Player.P1);
        CardInstance ladyOnBoard = new CardInstance(lady, Player.P1);
        CardInstance trebuchetOnBoard = new CardInstance(trebuchet, Player.P1);
        CardInstance engineerOnBoard = new CardInstance(engineer, Player.P1);
        state.playerState(Player.P1).board().close().addUnit(richardOnBoard);
        state.playerState(Player.P1).board().close().addUnit(ladyOnBoard);
        state.playerState(Player.P1).board().siege().addUnit(trebuchetOnBoard);
        state.playerState(Player.P1).board().siege().addUnit(engineerOnBoard);

        ParseResult result = parser.parse("play 1 unit-2", state, Player.P1);

        assertThat(result).isInstanceOf(ParseResult.ParseSuccess.class);
        PlayCardMove move = (PlayCardMove) ((ParseResult.ParseSuccess) result).move();
        assertThat(move.targetInstanceId()).isEqualTo(ladyOnBoard.instanceId());
    }

    private GameState emptyState() {
        return MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
    }
}
