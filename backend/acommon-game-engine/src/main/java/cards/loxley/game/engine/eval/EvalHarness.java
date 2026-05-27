package cards.loxley.game.engine.eval;

import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.GameStateFactory;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.bot.BotStrategy;
import cards.loxley.game.engine.execution.TurnOrchestrator;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Component
public class EvalHarness {

    private static final int TURN_CAP = 500;

    private final GameStateFactory factory;
    private final TurnOrchestrator orchestrator;
    private final MoveGenerator generator;
    private final GameDefinition definition;

    public EvalHarness(
            GameStateFactory factory,
            TurnOrchestrator orchestrator,
            MoveGenerator generator,
            GameDefinition definition
    ) {
        this.factory = factory;
        this.orchestrator = orchestrator;
        this.generator = generator;
        this.definition = definition;
    }

    public EvalResult runMatches(BotStrategy bot1, BotStrategy bot2, int gameCount, long seedBase) {
        return runMatchesWithDecks(bot1, definition.deck(), bot2, definition.deck(), gameCount, seedBase);
    }

    public EvalResult runMatchesWithDecks(BotStrategy bot1,
                                          cards.loxley.game.domain.card.Deck deckBot1,
                                          BotStrategy bot2,
                                          cards.loxley.game.domain.card.Deck deckBot2,
                                          int gameCount,
                                          long seedBase) {
        int bot1Wins = 0;
        int bot2Wins = 0;
        int draws = 0;
        List<EvalGame> games = new ArrayList<>(gameCount);

        for (int i = 0; i < gameCount; i++) {
            EvalGame game = runOneMatch(bot1, deckBot1, bot2, deckBot2, seedBase + i);
            games.add(game);
            Optional<Player> winner = game.winner();
            if (winner.isEmpty()) {
                draws++;
            } else if (winner.get() == Player.P1) {
                bot1Wins++;
            } else {
                bot2Wins++;
            }
        }

        return new EvalResult(
                bot1.name(), bot2.name(), gameCount,
                bot1Wins, bot2Wins, draws, List.copyOf(games)
        );
    }

    private EvalGame runOneMatch(BotStrategy bot1,
                                 cards.loxley.game.domain.card.Deck deckBot1,
                                 BotStrategy bot2,
                                 cards.loxley.game.domain.card.Deck deckBot2,
                                 long seed) {
        GameState state = factory.newGame(deckBot1, deckBot2, new Random(seed));
        int turns = 0;
        while (!state.matchEnded() && turns < TURN_CAP) {
            Player current = state.currentTurn();
            BotStrategy bot = current == Player.P1 ? bot1 : bot2;
            List<Move> moves = generator.legalMoves(state, current);
            Move chosen = bot.chooseMove(state, current, moves);
            orchestrator.playTurn(state, chosen);
            turns++;
        }

        return new EvalGame(
                seed,
                state.matchWinner(),
                state.playerState(Player.P1).roundsWon(),
                state.playerState(Player.P2).roundsWon(),
                turns
        );
    }
}
