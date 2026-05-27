package cards.loxley.game.engine.bot;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.domain.state.PlayerStateTestSupport;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveGenerator;
import cards.loxley.game.engine.move.MoveTestFixtures;
import cards.loxley.game.engine.move.PassMove;
import cards.loxley.game.engine.move.PlayCardMove;
import cards.loxley.game.engine.move.UseLeaderMove;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HeuristicMediumBotTest {

    @Autowired
    HeuristicMediumBot bot;

    @Autowired
    MoveGenerator generator;

    @Autowired
    GameDefinition definition;

    @Test
    void passesWhenLeadingAndOpponentPassed() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        PlayerStateTestSupport.markPassed(state.playerState(Player.P2));

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(PassMove.class);
    }

    @Test
    void refusesToPassWhenLosingAndHandHasCards() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));
        PlayerStateTestSupport.markPassed(state.playerState(Player.P2));
        // P1 losing 0 vs 16, opponent passed → must keep playing.

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isNotInstanceOf(PassMove.class);
    }

    @Test
    void hornMovePicksRowWithHighestCurrentStrength() {
        Card horn = MoveTestFixtures.cardById(definition, "sherwood_horn");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card much = MoveTestFixtures.cardById(definition, "much_millers_son");
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(horn), List.of());
        // P1: CLOSE = 5+5 = 10, RANGED = 6, SIEGE = 0; total 16.
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P1).board().ranged().addUnit(new CardInstance(much, Player.P1));
        // P2: three Bracia in tight bond = 4*3*3 = 36; ensures P1 is not leading,
        // so Pass scores 0 and Horn-on-CLOSE (score = 10) dominates Horn-on-RANGED (6).
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(PlayCardMove.class);
        PlayCardMove play = (PlayCardMove) chosen;
        assertThat(play.targetRow()).isEqualTo(RowId.CLOSE);
    }

    @Test
    void scorchOnlyPlayedWhenEnemyHasMeaningfulCards() {
        Card scorch = MoveTestFixtures.cardById(definition, "fire_arrows");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");

        // Case A: empty enemy board → Scorch scores -100, Lady scores 5 → Lady wins.
        GameState emptyEnemy = MoveTestFixtures.gameStateWith(definition, List.of(scorch, lady), List.of());
        Move emptyChoice = bot.chooseMove(emptyEnemy, Player.P1, generator.legalMoves(emptyEnemy, Player.P1));
        PlayCardMove emptyPlay = (PlayCardMove) emptyChoice;
        CardInstance emptyCard = findHandCard(emptyEnemy.playerState(Player.P1), emptyPlay.handInstanceId());
        assertThat(emptyCard.card().id()).isEqualTo("lady_marian");

        // Case B: enemy has 3 Bracia (each currentStrength = 12) → Scorch scores 36, Lady scores 5 → Scorch wins.
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        GameState bigEnemy = MoveTestFixtures.gameStateWith(definition, List.of(scorch, lady), List.of());
        bigEnemy.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));
        bigEnemy.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));
        bigEnemy.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));
        Move bigChoice = bot.chooseMove(bigEnemy, Player.P1, generator.legalMoves(bigEnemy, Player.P1));
        PlayCardMove bigPlay = (PlayCardMove) bigChoice;
        CardInstance bigCard = findHandCard(bigEnemy.playerState(Player.P1), bigPlay.handInstanceId());
        assertThat(bigCard.card().id()).isEqualTo("fire_arrows");
    }

    @Test
    void useLeaderPreferredWhenWeatherActiveOnOwnBoard() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());
        state.playerState(Player.P1).board().close().applyWeather();

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(UseLeaderMove.class);
    }

    @Test
    void evaluateMoveProducesExpectedScoresForKeyShapes() {
        Card horn = MoveTestFixtures.cardById(definition, "sherwood_horn");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(horn, lady), List.of());
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        // CLOSE row strength = 10.

        PlayerState ps = state.playerState(Player.P1);
        String ladyHandId = ps.hand().stream()
                .filter(c -> c.card().id().equals("lady_marian"))
                .findFirst().orElseThrow().instanceId();
        String hornHandId = ps.hand().stream()
                .filter(c -> c.card().id().equals("sherwood_horn"))
                .findFirst().orElseThrow().instanceId();

        int ladyScore = bot.evaluateMove(state, Player.P1,
                PlayCardMove.unit(Player.P1, ladyHandId, RowId.CLOSE));
        int hornCloseScore = bot.evaluateMove(state, Player.P1,
                PlayCardMove.specialOnRow(Player.P1, hornHandId, RowId.CLOSE));
        int hornSiegeScore = bot.evaluateMove(state, Player.P1,
                PlayCardMove.specialOnRow(Player.P1, hornHandId, RowId.SIEGE));

        assertThat(ladyScore).isEqualTo(5);
        assertThat(hornCloseScore).isEqualTo(10);
        assertThat(hornSiegeScore).isZero();
    }

    @Test
    void mediumDoesNotPrematurelyPassWhenAhead() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        // P1 hand has 5 cards (4 brothers, 1 lady), P2 hand has 5 cards (5 brothers).
        GameState state = MoveTestFixtures.gameStateWith(
                definition,
                List.of(brothers, brothers, brothers, brothers, lady),
                List.of(brothers, brothers, brothers, brothers, brothers));
        // P1 leading 10 - 5 on board, opp NOT passed → must keep playing.
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(lady, Player.P2));

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isNotInstanceOf(PassMove.class);
    }

    @Test
    void mediumPassesWhenLeadingAndOppPassed() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());
        // P1 leading 10 - 5, opp passed → Pass.
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(lady, Player.P2));
        PlayerStateTestSupport.markPassed(state.playerState(Player.P2));

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(PassMove.class);
    }

    @Test
    void mediumBotDecoysHighValueTightBondUnitOverWeakerOptions() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card trebuchet = MoveTestFixtures.cardById(definition, "sherwood_trebuchet");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card much = MoveTestFixtures.cardById(definition, "much_millers_son");
        // P2 hand size matches P1's so Pass falls to the +10 case, not +50, leaving room
        // for Decoy-on-Trebusz (score 11) to win.
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of(much));
        CardInstance trebuchetOnBoard = new CardInstance(trebuchet, Player.P1);
        CardInstance ladyOnBoard = new CardInstance(lady, Player.P1);
        state.playerState(Player.P1).board().siege().addUnit(trebuchetOnBoard);
        state.playerState(Player.P1).board().close().addUnit(ladyOnBoard);

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(PlayCardMove.class);
        PlayCardMove play = (PlayCardMove) chosen;
        CardInstance handCard = findHandCard(state.playerState(Player.P1), play.handInstanceId());
        assertThat(handCard.card().id()).isEqualTo("scarecrow");
        assertThat(play.targetInstanceId()).isEqualTo(trebuchetOnBoard.instanceId());
    }

    private CardInstance findHandCard(PlayerState ps, String instanceId) {
        return ps.hand().stream()
                .filter(c -> c.instanceId().equals(instanceId))
                .findFirst().orElseThrow();
    }
}
