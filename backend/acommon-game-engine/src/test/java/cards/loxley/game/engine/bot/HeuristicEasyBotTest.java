package cards.loxley.game.engine.bot;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HeuristicEasyBotTest {

    @Autowired
    HeuristicEasyBot bot;

    @Autowired
    MoveGenerator generator;

    @Autowired
    GameDefinition definition;

    @Test
    void passesWhenLeadingAndOpponentHasPassed() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());
        PlayerState p1 = state.playerState(Player.P1);
        p1.board().close().addUnit(new CardInstance(lady, Player.P1));
        PlayerStateTestSupport.markPassed(state.playerState(Player.P2));

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(PassMove.class);
    }

    @Test
    void prefersScorchWhenOpponentBoardHasUnitAtOrAboveThreshold() {
        Card scorch = MoveTestFixtures.cardById(definition, "fire_arrows");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(scorch, lady), List.of());
        // Tight bond pair of Bracia → each currentStrength = 8.
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(PlayCardMove.class);
        PlayCardMove play = (PlayCardMove) chosen;
        CardInstance playedCard = state.playerState(Player.P1).hand().stream()
                .filter(c -> c.instanceId().equals(play.handInstanceId()))
                .findFirst().orElseThrow();
        assertThat(playedCard.card().id()).isEqualTo("fire_arrows");
    }

    @Test
    void picksFirstNonPassMoveWhenNoSpecialConditionApplies() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady, lady, lady, lady), List.of());

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(PlayCardMove.class);
    }

    @Test
    void doesNotPassWhenLosingEvenWithFewCardsInHand() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        // 1 card in hand (1 < 4 but 1 % 3 != 0 → bot won't auto-pass).
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(lady), List.of());
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(brothers, Player.P2));
        // P1 is losing 0 vs 16.

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isNotInstanceOf(PassMove.class);
    }

    @Test
    void easyBotDoesNotPlayClearWeatherWithoutActiveWeather() {
        Card sun = MoveTestFixtures.cardById(definition, "sherwood_sun");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(sun), List.of());

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        // Either Pass or UseLeader is fine; what matters is no Clear Weather.
        if (chosen instanceof PlayCardMove play) {
            CardInstance ci = state.playerState(Player.P1).hand().stream()
                    .filter(c -> c.instanceId().equals(play.handInstanceId()))
                    .findFirst().orElse(null);
            assertThat(ci).matches(c -> c == null || !c.card().id().equals("sherwood_sun"));
        }
    }

    @Test
    void easyBotPlaysClearWeatherIfWeatherIsActiveOnOwnBoard() {
        Card sun = MoveTestFixtures.cardById(definition, "sherwood_sun");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(sun), List.of());
        state.playerState(Player.P1).board().close().applyWeather();

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(PlayCardMove.class);
        PlayCardMove play = (PlayCardMove) chosen;
        CardInstance ci = state.playerState(Player.P1).hand().stream()
                .filter(c -> c.instanceId().equals(play.handInstanceId()))
                .findFirst().orElseThrow();
        assertThat(ci.card().id()).isEqualTo("sherwood_sun");
    }

    @Test
    void easyBotSkipsDecoyOnLowPowerTarget() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card peasants = MoveTestFixtures.cardById(definition, "peasants_of_locksley");
        Card richard = MoveTestFixtures.cardById(definition, "sir_richard_lea");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(peasants, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(peasants, Player.P1));
        CardInstance richardOnBoard = new CardInstance(richard, Player.P1);
        state.playerState(Player.P1).board().close().addUnit(richardOnBoard);

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        // Only legal play after filtering low-power decoys is Decoy → Sir Richard.
        assertThat(chosen).isInstanceOf(PlayCardMove.class);
        PlayCardMove play = (PlayCardMove) chosen;
        assertThat(play.targetInstanceId()).isEqualTo(richardOnBoard.instanceId());
    }

    @Test
    void easyBotSkipsAllDecoysWhenAllTargetsAreWeak() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card peasants = MoveTestFixtures.cardById(definition, "peasants_of_locksley");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy, lady), List.of());
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(peasants, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(peasants, Player.P1));

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        // Bot should pick Lady Marian (unit) rather than any Decoy.
        assertThat(chosen).isInstanceOf(PlayCardMove.class);
        PlayCardMove play = (PlayCardMove) chosen;
        CardInstance ci = state.playerState(Player.P1).hand().stream()
                .filter(c -> c.instanceId().equals(play.handInstanceId()))
                .findFirst().orElseThrow();
        assertThat(ci.card().id()).isEqualTo("lady_marian");
        assertThat(play.targetInstanceId()).isNull();
    }

    @Test
    void easyBotDoesNotPlayWeatherOnRowWhereLeading() {
        Card icyDawn = MoveTestFixtures.cardById(definition, "icy_dawn");
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(icyDawn), List.of());
        // P1 leads CLOSE with 16 (2× Bracia tight bond), P2 CLOSE empty.
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(brothers, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(brothers, Player.P1));

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        if (chosen instanceof PlayCardMove play) {
            CardInstance ci = state.playerState(Player.P1).hand().stream()
                    .filter(c -> c.instanceId().equals(play.handInstanceId()))
                    .findFirst().orElse(null);
            assertThat(ci).matches(c -> c == null || !c.card().id().equals("icy_dawn"));
        }
    }

    @Test
    void easyBotDoesNotDecoyOpponentLowPowerSpy() {
        // Opponent's Hugo Łapserdak (Spy, basePower=1) sits on my SIEGE after their spy play.
        // Easy's Rule 5 must filter this Decoy move because basePower 1 ≤ 3.
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card hugo = MoveTestFixtures.cardById(definition, "hugo_rascal");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy, lady), List.of());
        CardInstance opponentSpyOnMyBoard = new CardInstance(hugo, Player.P2);
        state.playerState(Player.P1).board().siege().addUnit(opponentSpyOnMyBoard);

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        if (chosen instanceof PlayCardMove play) {
            assertThat(play.targetInstanceId())
                    .as("must NOT decoy a low-power opponent spy")
                    .isNotEqualTo(opponentSpyOnMyBoard.instanceId());
        }
    }

    @Test
    void easyBotMayDecoyOpponentHighPowerSpyOnOwnBoard() {
        // Opponent's Lord Walter (Spy, basePower=5) sits on my CLOSE after their spy play.
        // basePower 5 > 3 → not filtered by Rule 5 → bot picks Decoy because hand has no UNIT plays.
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card lordWalter = MoveTestFixtures.cardById(definition, "lord_walter_huntingdon");
        // Pure-special hand so Rule 7 (prefer UNIT) doesn't dominate.
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        CardInstance opponentSpyOnMyBoard = new CardInstance(lordWalter, Player.P1.opponent());
        state.playerState(Player.P1).board().close().addUnit(opponentSpyOnMyBoard);

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(PlayCardMove.class);
        PlayCardMove play = (PlayCardMove) chosen;
        assertThat(play.targetInstanceId())
                .as("with no other special and no UNIT in hand, decoy on the high-power opponent spy is picked")
                .isEqualTo(opponentSpyOnMyBoard.instanceId());
    }

    @Test
    void easyBotPrefersUnitOverSpecial() {
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        Card fog = MoveTestFixtures.cardById(definition, "sherwood_fog");
        Card eleanor = MoveTestFixtures.cardById(definition, "eleanor_archer");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(brothers, fog), List.of());
        // Opponent leads RANGED → fog is technically advantageous, so rule 6 won't filter it.
        state.playerState(Player.P2).board().ranged().addUnit(new CardInstance(eleanor, Player.P2));

        Move chosen = bot.chooseMove(state, Player.P1, generator.legalMoves(state, Player.P1));

        assertThat(chosen).isInstanceOf(PlayCardMove.class);
        PlayCardMove play = (PlayCardMove) chosen;
        CardInstance ci = state.playerState(Player.P1).hand().stream()
                .filter(c -> c.instanceId().equals(play.handInstanceId()))
                .findFirst().orElseThrow();
        assertThat(ci.card().id()).isEqualTo("sherwood_brothers");
    }
}
