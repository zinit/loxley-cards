package cards.loxley.cli;

import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.GameStateFactory;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.bot.BotStrategy;
import cards.loxley.game.engine.bot.BotStrategyResolver;
import cards.loxley.game.engine.bot.HeuristicEasyBot;
import cards.loxley.game.engine.bot.HeuristicMediumBot;
import cards.loxley.game.engine.bot.RandomBot;
import cards.loxley.game.engine.eval.EvalHarness;
import cards.loxley.game.engine.eval.EvalResult;
import cards.loxley.game.engine.opponent.DeckVariant;
import cards.loxley.game.engine.opponent.DeckVariantLoader;
import cards.loxley.game.engine.opponent.OpponentProfile;
import cards.loxley.game.engine.opponent.OpponentProfileRegistry;
import cards.loxley.game.domain.card.Deck;
import cards.loxley.game.engine.execution.TurnOrchestrator;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveDescriber;
import cards.loxley.game.engine.move.MoveGenerator;
import cards.loxley.game.engine.move.PlayCardMove;
import cards.loxley.game.engine.scoring.BoardScorer;
import cards.loxley.game.engine.scoring.RowScorer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@SpringBootApplication(scanBasePackages = {"cards.loxley.cli", "cards.loxley.game"})
public class LoxleyCliApplication {

    private static final int MAX_SIMULATION_TURNS = 300;
    private static final int EVAL_GAMES_PER_MATCHUP = 50;
    private static final int PROFILE_EVAL_GAMES = 30;
    private static final String PROXY_PLAYER_DECK_ID = "standard_deck";

    public static void main(String[] args) {
        SpringApplication.run(LoxleyCliApplication.class, args);
    }

    @Bean
    @Profile("!cli-player")
    public CommandLineRunner startupRunner(
            GameDefinition def,
            GameStateFactory factory,
            RowScorer rowScorer,
            BoardScorer boardScorer,
            MoveGenerator moveGenerator,
            MoveDescriber moveDescriber,
            TurnOrchestrator orchestrator,
            EvalHarness evalHarness,
            HeuristicEasyBot easyBot,
            HeuristicMediumBot mediumBot,
            OpponentProfileRegistry opponentProfileRegistry,
            DeckVariantLoader deckVariantLoader,
            BotStrategyResolver botStrategyResolver
    ) {
        return args -> {
            System.out.println("Loxley Cards engine — initialized");
            System.out.printf(
                    "Loaded ruleset: %s, faction: %s, cards: %d, playable copies: %d%n",
                    def.ruleset().name(),
                    def.deck().faction(),
                    def.cards().size(),
                    def.deck().summary().totalPlayableCardCopies()
            );

            GameState state = factory.newGame(def.deck(), def.deck());
            PlayerState p1 = state.playerState(Player.P1);
            PlayerState p2 = state.playerState(Player.P2);
            int maxRounds = def.ruleset().matchFormat().maxRounds();

            System.out.println("Game initialized.");
            System.out.printf(
                    "P1 hand: %d cards, P1 deck: %d remaining, P1 leader: %s.%n",
                    p1.hand().size(), p1.deck().size(), p1.leader().card().name()
            );
            System.out.printf(
                    "P2 hand: %d cards, P2 deck: %d remaining, P2 leader: %s.%n",
                    p2.hand().size(), p2.deck().size(), p2.leader().card().name()
            );
            System.out.printf(
                    "Current turn: %s, round %d/%d.%n",
                    state.currentTurn(), state.roundNumber(), maxRounds
            );

            placeBrothersDemo(p1);

            System.out.printf("P1 CLOSE row strength: %d%n", rowScorer.rowStrength(p1.board().close()));
            System.out.printf("P1 RANGED row strength: %d%n", rowScorer.rowStrength(p1.board().ranged()));
            System.out.printf("P1 SIEGE row strength: %d%n", rowScorer.rowStrength(p1.board().siege()));
            System.out.printf("P1 total board strength: %d%n", boardScorer.sideStrength(p1.board()));
            System.out.printf("P2 total board strength: %d%n", boardScorer.sideStrength(p2.board()));

            List<Move> p1Moves = moveGenerator.legalMoves(state, Player.P1);
            System.out.printf("P1 legal moves: %d%n", p1Moves.size());
            int sampleSize = Math.min(5, p1Moves.size());
            for (int i = 0; i < sampleSize; i++) {
                System.out.printf("  %d. %s%n", i + 1, moveDescriber.describe(p1Moves.get(i), state));
            }

            System.out.println();
            runBotEvaluation(evalHarness, easyBot, mediumBot);

            System.out.println();
            runProfileEvaluation(evalHarness, factory, deckVariantLoader,
                    opponentProfileRegistry, botStrategyResolver, mediumBot);

            System.out.println();
            System.out.println("Starting bot-vs-bot simulation (fresh game, seeded random)...");
            simulateBotMatch(def, factory, moveGenerator, orchestrator);
        };
    }

    private static void runProfileEvaluation(
            EvalHarness evalHarness,
            GameStateFactory factory,
            DeckVariantLoader deckVariantLoader,
            OpponentProfileRegistry opponentProfileRegistry,
            BotStrategyResolver botStrategyResolver,
            HeuristicMediumBot proxyPlayer
    ) {
        System.out.println("=== Profile Evaluation ===");
        System.out.printf(
                "Running %d games per profile vs HeuristicMediumBot (proxy for skilled player)...%n",
                PROFILE_EVAL_GAMES);
        Deck proxyDeck = factory.toDeck(deckVariantLoader.findById(PROXY_PLAYER_DECK_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Proxy player deck variant '" + PROXY_PLAYER_DECK_ID + "' missing")));
        long seedBase = 500L;
        for (OpponentProfile profile : opponentProfileRegistry.all()) {
            DeckVariant variant = deckVariantLoader.findById(profile.deckVariantId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing deck variant " + profile.deckVariantId()
                                    + " for profile " + profile.id()));
            Deck opponentDeck = factory.toDeck(variant);
            BotStrategy opponentBot = botStrategyResolver.resolve(profile.strategyName());

            EvalResult result = evalHarness.runMatchesWithDecks(
                    opponentBot, opponentDeck,
                    proxyPlayer, proxyDeck,
                    PROFILE_EVAL_GAMES, seedBase);
            int oppWins = result.bot1Wins();
            int proxyWins = result.bot2Wins();
            int draws = result.draws();
            int total = result.gameCount();
            System.out.printf(
                    "%-12s vs proxy_player (%d games): opponent=%d/%d (%d%%) proxy=%d/%d (%d%%) draws=%d/%d (%d%%)%n",
                    profile.id(), total,
                    oppWins, total, pct(oppWins, total),
                    proxyWins, total, pct(proxyWins, total),
                    draws, total, pct(draws, total));
            seedBase += PROFILE_EVAL_GAMES;
        }
        System.out.println("=== End Profile Evaluation ===");
    }

    private static int pct(int part, int total) {
        return total == 0 ? 0 : (part * 100) / total;
    }

    private static void runBotEvaluation(
            EvalHarness evalHarness,
            HeuristicEasyBot easyBot,
            HeuristicMediumBot mediumBot
    ) {
        System.out.println("=== Bot Evaluation ===");
        System.out.printf("Running %d games per matchup...%n", EVAL_GAMES_PER_MATCHUP);

        EvalResult randomVsRandom = evalHarness.runMatches(
                new RandomBot(11), new RandomBot(12), EVAL_GAMES_PER_MATCHUP, 1L);
        System.out.println(randomVsRandom.summary());

        EvalResult easyVsRandom = evalHarness.runMatches(
                easyBot, new RandomBot(21), EVAL_GAMES_PER_MATCHUP, 100L);
        System.out.println(easyVsRandom.summary());

        EvalResult mediumVsEasy = evalHarness.runMatches(
                mediumBot, easyBot, EVAL_GAMES_PER_MATCHUP, 200L);
        System.out.println(mediumVsEasy.summary());

        EvalResult mediumVsRandom = evalHarness.runMatches(
                mediumBot, new RandomBot(31), EVAL_GAMES_PER_MATCHUP, 300L);
        System.out.println(mediumVsRandom.summary());
        System.out.println("=== End Evaluation ===");
    }

    private static void placeBrothersDemo(PlayerState p1) {
        List<CardInstance> brothers = p1.hand().stream()
                .filter(ci -> ci.card().id().equals("sherwood_brothers"))
                .limit(2)
                .toList();

        if (brothers.size() < 2) {
            System.out.printf(
                    "Demo placement skipped: found %d Bracia z Sherwood in P1 hand (need 2).%n",
                    brothers.size()
            );
            return;
        }

        for (CardInstance ci : brothers) {
            p1.removeFromHand(ci.instanceId());
            p1.board().close().addUnit(ci);
        }
        System.out.println("Placed 2x Bracia z Sherwood on P1 CLOSE row.");
    }

    private static void simulateBotMatch(
            GameDefinition def,
            GameStateFactory factory,
            MoveGenerator moveGenerator,
            TurnOrchestrator orchestrator
    ) {
        GameState state = factory.newGame(def.deck(), def.deck());
        Random botRandom = new Random(42);
        int turn = 0;
        int p1CardsThisRound = 0;
        int p2CardsThisRound = 0;

        while (!state.matchEnded() && turn < MAX_SIMULATION_TURNS) {
            Player current = state.currentTurn();
            List<Move> legal = moveGenerator.legalMoves(state, current);
            if (legal.isEmpty()) {
                throw new IllegalStateException(
                        "No legal moves available for " + current
                                + " on turn " + turn + " — Pass should always be legal");
            }
            Move chosen = legal.get(botRandom.nextInt(legal.size()));
            if (chosen instanceof PlayCardMove) {
                if (current == Player.P1) p1CardsThisRound++;
                else p2CardsThisRound++;
            }

            int roundsBefore = state.roundHistory().size();
            int p1HandBefore = state.playerState(Player.P1).hand().size();
            int p2HandBefore = state.playerState(Player.P2).hand().size();
            List<String> weathersSnapshot = activeWeatherRows(state);
            List<String> hornsSnapshot = activeHorns(state);

            orchestrator.playTurn(state, chosen);
            turn++;

            if (state.roundHistory().size() > roundsBefore) {
                var justFinished = state.roundHistory().get(roundsBefore);
                int finishedRound = justFinished.roundNumber();
                System.out.printf(
                        "Round %d ended: P1 played %d cards, P2 played %d, weathers active: %s, horns active: %s%n",
                        finishedRound,
                        p1CardsThisRound, p2CardsThisRound,
                        weathersSnapshot, hornsSnapshot
                );
                justFinished.winner().ifPresent(winner -> {
                    int handBefore = winner == Player.P1 ? p1HandBefore : p2HandBefore;
                    int handAfter = state.playerState(winner).hand().size();
                    int drawn = handAfter - handBefore;
                    if (drawn > 0) {
                        System.out.printf(
                                "Round %d won by %s: drew %d card via faction passive (hand: %d → %d)%n",
                                finishedRound, winner, drawn, handBefore, handAfter
                        );
                    }
                });
                p1CardsThisRound = 0;
                p2CardsThisRound = 0;
            }
        }

        if (!state.matchEnded()) {
            System.out.printf(
                    "Bot match aborted after %d turns (cap reached).%n", MAX_SIMULATION_TURNS);
            return;
        }

        String winnerLabel = state.matchWinner().map(Enum::name).orElse("TIE");
        String rounds = state.roundHistory().stream()
                .map(r -> String.format(
                        "r%d: %d-%d (%s)",
                        r.roundNumber(),
                        r.p1Score(),
                        r.p2Score(),
                        r.winner().map(Enum::name).orElse("TIE")))
                .collect(Collectors.joining(", "));

        System.out.printf(
                "Match ended after %d rounds (turns played: %d). Winner: %s.%n",
                state.roundHistory().size(), turn, winnerLabel);
        System.out.printf("Round results: %s%n", rounds);
        System.out.printf(
                "P1 graveyard: %d cards, P2 graveyard: %d cards%n",
                state.playerState(Player.P1).graveyard().size(),
                state.playerState(Player.P2).graveyard().size()
        );
    }

    private static List<String> activeWeatherRows(GameState state) {
        List<String> active = new ArrayList<>();
        for (RowId r : RowId.values()) {
            if (state.playerState(Player.P1).board().row(r).weatherActive()) {
                active.add(r.name());
            }
        }
        return active;
    }

    private static List<String> activeHorns(GameState state) {
        List<String> active = new ArrayList<>();
        for (Player p : Player.values()) {
            for (RowId r : RowId.values()) {
                if (state.playerState(p).board().row(r).hornActive()) {
                    active.add(p.name() + ":" + r.name());
                }
            }
        }
        return active;
    }
}
