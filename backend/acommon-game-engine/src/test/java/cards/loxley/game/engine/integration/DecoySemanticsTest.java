package cards.loxley.game.engine.integration;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.execution.MoveExecutor;
import cards.loxley.game.engine.execution.TurnOrchestrator;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveGenerator;
import cards.loxley.game.engine.move.MoveTestFixtures;
import cards.loxley.game.engine.move.MoveValidator;
import cards.loxley.game.engine.move.PlayCardMove;
import cards.loxley.game.engine.move.ValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DecoySemanticsTest {

    @Autowired
    MoveGenerator generator;

    @Autowired
    MoveValidator validator;

    @Autowired
    TurnOrchestrator orchestrator;

    @Autowired
    MoveExecutor executor;

    @Autowired
    GameDefinition definition;

    @Test
    void decoyCanTakeOpponentSpyFromOwnBoard() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card hugo = MoveTestFixtures.cardById(definition, "hugo_rascal");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        // P2 already played their Spy → Hugo physically on P1.SIEGE, owner = P2.
        // (Spy ability already fired on play — we are NOT re-running it; we just set up the post-play state.)
        CardInstance opponentSpyOnMyBoard = new CardInstance(hugo, Player.P2);
        state.playerState(Player.P1).board().siege().addUnit(opponentSpyOnMyBoard);

        int p1HandBefore = state.playerState(Player.P1).hand().size(); // 1: just the decoy
        int p2HandBefore = state.playerState(Player.P2).hand().size(); // 0

        List<Move> legal = generator.legalMoves(state, Player.P1);
        Move decoyOnHugo = legal.stream()
                .filter(m -> m instanceof PlayCardMove pcm
                        && opponentSpyOnMyBoard.instanceId().equals(pcm.targetInstanceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Decoy on opponent spy was not in legal moves"));

        orchestrator.playTurn(state, decoyOnHugo);

        // Hugo no longer physically on P1's SIEGE.
        assertThat(state.playerState(Player.P1).board().siege().units())
                .extracting(CardInstance::instanceId)
                .doesNotContain(opponentSpyOnMyBoard.instanceId());

        // Hugo is in P1's hand, with owner = P1 (ownership transferred).
        Optional<CardInstance> hugoInP1Hand = state.playerState(Player.P1).hand().stream()
                .filter(ci -> ci.card().id().equals("hugo_rascal"))
                .findFirst();
        assertThat(hugoInP1Hand).isPresent();
        assertThat(hugoInP1Hand.get().owner()).isEqualTo(Player.P1);

        // Hand size math: P1 lost the decoy (-1), gained Hugo (+1) → net unchanged.
        assertThat(state.playerState(Player.P1).hand().size()).isEqualTo(p1HandBefore);
        // P2 hand untouched — Spy does NOT retrigger when taken via Decoy.
        assertThat(state.playerState(Player.P2).hand().size()).isEqualTo(p2HandBefore);
    }

    @Test
    void decoyTakenSpyTriggersAgainWhenReplayed() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card hugo = MoveTestFixtures.cardById(definition, "hugo_rascal");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card leader = MoveTestFixtures.leaderCard(definition);
        // P1 hand: decoy. P1 has 5 cards in deck so Spy redraw can pull 2.
        cards.loxley.game.domain.state.PlayerState p1 = MoveTestFixtures.playerWithHandAndDeck(
                Player.P1, leader, List.of(decoy), List.of(lady, lady, lady, lady, lady));
        cards.loxley.game.domain.state.PlayerState p2 = MoveTestFixtures.playerWithHand(
                Player.P2, leader, List.of());
        CardInstance opponentSpyOnMyBoard = new CardInstance(hugo, Player.P2);
        p1.board().siege().addUnit(opponentSpyOnMyBoard);
        GameState state = new GameState(p1, p2, Player.P1);

        // Step 1: P1 plays Decoy on Hugo → Hugo to P1 hand with owner = P1.
        // (Direct executor — bypasses turn rotation/round resolution since we drive two
        // P1 moves back-to-back here to isolate the Decoy → replay sequence.)
        String decoyHandId = p1.hand().get(0).instanceId();
        executor.execute(state, PlayCardMove.specialOnUnit(
                Player.P1, decoyHandId, opponentSpyOnMyBoard.instanceId()));

        CardInstance hugoInHand = p1.hand().stream()
                .filter(ci -> ci.card().id().equals("hugo_rascal"))
                .findFirst().orElseThrow();
        assertThat(hugoInHand.owner()).isEqualTo(Player.P1);

        int p1DeckBeforeReplay = p1.deck().size();
        int p1HandBeforeReplay = p1.hand().size();
        int p2HandBeforeReplay = p2.hand().size();

        // Step 2: P1 plays Hugo from hand as a normal Spy move.
        executor.execute(state, PlayCardMove.spy(Player.P1, hugoInHand.instanceId(), RowId.SIEGE));

        // Hugo lands on P2.SIEGE (Spy goes to opponent's board) with owner = P1.
        Optional<CardInstance> hugoOnBotBoard = p2.board().siege().findUnit(hugoInHand.instanceId());
        assertThat(hugoOnBotBoard).isPresent();
        assertThat(hugoOnBotBoard.get().owner()).isEqualTo(Player.P1);

        // Spy retriggers normally: P1 drew 2 cards (hand: -1 played +2 drew = net +1; deck: -2).
        assertThat(p1.hand().size()).isEqualTo(p1HandBeforeReplay - 1 + 2);
        assertThat(p1.deck().size()).isEqualTo(p1DeckBeforeReplay - 2);
        assertThat(p2.hand().size()).isEqualTo(p2HandBeforeReplay);
    }

    @Test
    void decoyCannotTargetHeroEvenOnMyBoard() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card littleJohn = MoveTestFixtures.cardById(definition, "little_john"); // HERO
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        // Hypothetical edge case: opponent's hero physically on my board (no such card has SPY+HERO,
        // but the validator should still reject if reached).
        CardInstance opponentHeroOnMyBoard = new CardInstance(littleJohn, Player.P2);
        state.playerState(Player.P1).board().close().addUnit(opponentHeroOnMyBoard);

        String decoyHandId = state.playerState(Player.P1).hand().get(0).instanceId();

        // Generator should not propose it.
        List<Move> legal = generator.legalMoves(state, Player.P1);
        assertThat(legal)
                .filteredOn(m -> m instanceof PlayCardMove pcm
                        && opponentHeroOnMyBoard.instanceId().equals(pcm.targetInstanceId()))
                .as("decoy must never target a hero")
                .isEmpty();

        // Direct validator call: rejected with hero message.
        ValidationResult result = validator.validate(state,
                PlayCardMove.specialOnUnit(Player.P1, decoyHandId, opponentHeroOnMyBoard.instanceId()));
        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason()).contains("Hero");
    }

    @Test
    void decoyCanStillTargetOwnSpyOnOpponentBoard() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card lordWalter = MoveTestFixtures.cardById(definition, "lord_walter_huntingdon");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        // P1 already played their Spy → Lord Walter physically on P2.CLOSE, owner = P1.
        CardInstance mySpyOnOpponentBoard = new CardInstance(lordWalter, Player.P1);
        state.playerState(Player.P2).board().close().addUnit(mySpyOnOpponentBoard);

        List<Move> legal = generator.legalMoves(state, Player.P1);
        assertThat(legal)
                .filteredOn(m -> m instanceof PlayCardMove pcm
                        && mySpyOnOpponentBoard.instanceId().equals(pcm.targetInstanceId()))
                .as("regression: decoy should still target own spy on opponent's board")
                .hasSize(1);

        String decoyHandId = state.playerState(Player.P1).hand().get(0).instanceId();
        ValidationResult result = validator.validate(state,
                PlayCardMove.specialOnUnit(Player.P1, decoyHandId, mySpyOnOpponentBoard.instanceId()));
        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void decoyCannotTargetOpponentRegularUnitOnOpponentSide() {
        Card decoy = MoveTestFixtures.cardById(definition, "scarecrow");
        Card peasants = MoveTestFixtures.cardById(definition, "peasants_of_locksley");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(decoy), List.of());
        // P2's regular unit sits on P2's board — out of reach.
        CardInstance opponentUnit = new CardInstance(peasants, Player.P2);
        state.playerState(Player.P2).board().close().addUnit(opponentUnit);

        List<Move> legal = generator.legalMoves(state, Player.P1);
        assertThat(legal)
                .filteredOn(m -> m instanceof PlayCardMove pcm
                        && opponentUnit.instanceId().equals(pcm.targetInstanceId()))
                .as("decoy cannot reach opponent's units on the opponent's own board")
                .isEmpty();

        String decoyHandId = state.playerState(Player.P1).hand().get(0).instanceId();
        ValidationResult result = validator.validate(state,
                PlayCardMove.specialOnUnit(Player.P1, decoyHandId, opponentUnit.instanceId()));
        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(((ValidationResult.Invalid) result).reason())
                .isEqualTo("Decoy can only target cards on your side of the board or your own units anywhere");
    }
}
