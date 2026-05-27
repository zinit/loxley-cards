package cards.loxley.game.engine.integration;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.GameStateFactory;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.execution.MoveExecutor;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveGenerator;
import cards.loxley.game.engine.move.MoveTestFixtures;
import cards.loxley.game.engine.move.PlayCardMove;
import cards.loxley.game.engine.execution.TurnOrchestrator;
import cards.loxley.game.engine.scoring.RowScorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GameplayIntegrationTest {

    @Autowired
    MoveExecutor executor;

    @Autowired
    MoveGenerator generator;

    @Autowired
    TurnOrchestrator orchestrator;

    @Autowired
    GameStateFactory factory;

    @Autowired
    GameDefinition definition;

    @Autowired
    RowScorer rowScorer;

    @Test
    void spyDrawsTwoCardsAfterBeingPlayed() {
        Card allan = MoveTestFixtures.cardById(definition, "allan_a_dale");
        Card filler = MoveTestFixtures.cardById(definition, "lady_marian");
        Card leader = MoveTestFixtures.leaderCard(definition);

        PlayerState p1 = MoveTestFixtures.playerWithHandAndDeck(
                Player.P1, leader, List.of(allan), List.of(filler, filler, filler));
        PlayerState p2 = MoveTestFixtures.playerWithHand(Player.P2, leader, List.of());
        GameState state = new GameState(p1, p2, Player.P1);

        int handBefore = p1.hand().size();
        int deckBefore = p1.deck().size();
        String allanId = p1.hand().get(0).instanceId();

        executor.execute(state, PlayCardMove.spy(Player.P1, allanId, RowId.CLOSE));

        // Played 1 spy, drew 2 → hand grows by 1, deck shrinks by 2.
        assertThat(p1.hand()).hasSize(handBefore - 1 + 2);
        assertThat(p1.deck()).hasSize(deckBefore - 2);
        assertThat(p2.board().close().findUnit(allanId)).isPresent();
        assertThat(p2.board().close().findUnit(allanId).get().owner()).isEqualTo(Player.P1);
    }

    @Test
    void scorchDestroysAllMaxStrengthNonHeroCardsAcrossBothBoards() {
        Card fireArrows = MoveTestFixtures.cardById(definition, "fire_arrows");
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card littleJohn = MoveTestFixtures.cardById(definition, "little_john");
        Card leader = MoveTestFixtures.leaderCard(definition);

        PlayerState p1 = MoveTestFixtures.playerWithHand(Player.P1, leader, List.of(fireArrows));
        PlayerState p2 = MoveTestFixtures.playerWithHand(Player.P2, leader, List.of());
        CardInstance b1 = new CardInstance(brothers, Player.P1);
        CardInstance b2 = new CardInstance(brothers, Player.P1);
        CardInstance ladyP2 = new CardInstance(lady, Player.P2);
        CardInstance heroP2 = new CardInstance(littleJohn, Player.P2);
        p1.board().close().addUnit(b1);
        p1.board().close().addUnit(b2);
        p2.board().close().addUnit(ladyP2);
        p2.board().close().addUnit(heroP2);
        GameState state = new GameState(p1, p2, Player.P1);

        String scorchId = p1.hand().get(0).instanceId();

        executor.execute(state, PlayCardMove.special(Player.P1, scorchId));

        // Bracia tight-bond pair: each currentStrength = 4*2 = 8 → max. Both burn.
        // Lady (5) survives, Hero (10) untouched.
        assertThat(p1.board().close().findUnit(b1.instanceId())).isEmpty();
        assertThat(p1.board().close().findUnit(b2.instanceId())).isEmpty();
        assertThat(p2.board().close().findUnit(ladyP2.instanceId())).isPresent();
        assertThat(p2.board().close().findUnit(heroP2.instanceId())).isPresent();
        assertThat(p1.graveyard())
                .extracting(CardInstance::instanceId)
                .contains(b1.instanceId(), b2.instanceId(), scorchId);
    }

    @Test
    void weatherAndHornStackPerScoringFormula() {
        Card icyDawn = MoveTestFixtures.cardById(definition, "icy_dawn");
        Card horn = MoveTestFixtures.cardById(definition, "sherwood_horn");
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        Card leader = MoveTestFixtures.leaderCard(definition);

        PlayerState p1 = MoveTestFixtures.playerWithHand(
                Player.P1, leader, List.of(icyDawn, horn));
        PlayerState p2 = MoveTestFixtures.playerWithHand(Player.P2, leader, List.of());
        p1.board().close().addUnit(new CardInstance(brothers, Player.P1));
        p1.board().close().addUnit(new CardInstance(brothers, Player.P1));
        GameState state = new GameState(p1, p2, Player.P1);

        String icyDawnId = p1.hand().get(0).instanceId();
        executor.execute(state, PlayCardMove.special(Player.P1, icyDawnId));
        String hornId = p1.hand().get(0).instanceId();
        executor.execute(state,
                PlayCardMove.specialOnRow(Player.P1, hornId, RowId.CLOSE));

        // Weather forces base to 1, tight bond doubles to 2, horn doubles to 4 per card.
        // Two Bracia → row strength 8.
        assertThat(p1.board().close().weatherActive()).isTrue();
        assertThat(p1.board().close().hornActive()).isTrue();
        assertThat(rowScorer.rowStrength(p1.board().close())).isEqualTo(8);
    }

    @Test
    void randomBotMatchTerminatesAndProducesEndState() {
        GameState state = factory.newGame(definition.deck(), definition.deck());
        Random random = new Random(2026);
        int turnCap = 300;
        int turn = 0;

        while (!state.matchEnded() && turn < turnCap) {
            Player current = state.currentTurn();
            List<Move> legal = generator.legalMoves(state, current);
            Move chosen = legal.get(random.nextInt(legal.size()));
            orchestrator.playTurn(state, chosen);
            turn++;
        }

        assertThat(state.matchEnded()).isTrue();
        assertThat(state.roundHistory()).isNotEmpty();
        assertThat(turn).isLessThan(turnCap);
    }
}
