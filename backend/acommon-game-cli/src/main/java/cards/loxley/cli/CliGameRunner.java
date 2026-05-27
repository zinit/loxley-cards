package cards.loxley.cli;

import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.GameStateFactory;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.bot.BotStrategy;
import cards.loxley.game.engine.bot.BotStrategyResolver;
import cards.loxley.game.engine.campaign.CampaignStage;
import cards.loxley.game.engine.campaign.CampaignStageRegistry;
import cards.loxley.game.engine.execution.IllegalMoveException;
import cards.loxley.game.engine.execution.TurnOrchestrator;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveDescriber;
import cards.loxley.game.engine.move.MoveGenerator;
import cards.loxley.game.engine.opponent.OpponentProfile;
import cards.loxley.game.engine.opponent.OpponentProfileRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;

@Component
@Profile("cli-player")
public class CliGameRunner implements CommandLineRunner {

    private final GameStateFactory factory;
    private final TurnOrchestrator orchestrator;
    private final MoveGenerator generator;
    private final BoardRenderer boardRenderer;
    private final HandRenderer handRenderer;
    private final MoveParser parser;
    private final MoveDescriber describer;
    private final MovesListFormatter movesFormatter;
    private final CampaignStageRegistry stageRegistry;
    private final OpponentProfileRegistry profileRegistry;
    private final BotStrategyResolver botResolver;

    public CliGameRunner(
            GameStateFactory factory,
            TurnOrchestrator orchestrator,
            MoveGenerator generator,
            BoardRenderer boardRenderer,
            HandRenderer handRenderer,
            MoveParser parser,
            MoveDescriber describer,
            MovesListFormatter movesFormatter,
            CampaignStageRegistry stageRegistry,
            OpponentProfileRegistry profileRegistry,
            BotStrategyResolver botResolver
    ) {
        this.factory = factory;
        this.orchestrator = orchestrator;
        this.generator = generator;
        this.boardRenderer = boardRenderer;
        this.handRenderer = handRenderer;
        this.parser = parser;
        this.describer = describer;
        this.movesFormatter = movesFormatter;
        this.stageRegistry = stageRegistry;
        this.profileRegistry = profileRegistry;
        this.botResolver = botResolver;
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        printWelcome();
        boolean keepPlaying = true;
        while (keepPlaying) {
            CampaignStage stage = chooseStage(scanner);
            if (stage == null) {
                break;
            }
            playStage(scanner, stage);

            System.out.print("Play again? [y/n] > ");
            String line = readLine(scanner);
            keepPlaying = line.equalsIgnoreCase("y");
        }
        System.out.println("Thanks for playing!");
    }

    private void printWelcome() {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║        SHERWOOD CARDS                    ║");
        System.out.println("║   Robin Hood-themed turn-based cards     ║");
        System.out.println("╚══════════════════════════════════════════╝");
    }

    private CampaignStage chooseStage(Scanner scanner) {
        while (true) {
            System.out.println();
            System.out.println("Select campaign stage:");
            for (CampaignStage stage : stageRegistry.all()) {
                Optional<OpponentProfile> profile = profileRegistry.findById(stage.opponentProfileId());
                String profileTag = profile.map(p -> "(" + p.id() + ")").orElse("(?)");
                System.out.printf("  [%d] %s %s%n",
                        stage.stageNumber(), stage.description(), profileTag);
            }
            System.out.println("  [q] Quit");
            System.out.print("> ");
            String input = readLine(scanner).trim().toLowerCase();
            if (input.equals("q") || input.equals("quit") || input.equals("exit")) {
                return null;
            }
            try {
                int stageNumber = Integer.parseInt(input);
                Optional<CampaignStage> picked = stageRegistry.findByNumber(stageNumber);
                if (picked.isPresent()) {
                    return picked.get();
                }
            } catch (NumberFormatException ignored) {
                // fall through to error
            }
            System.out.println("Unknown choice: '" + input + "'");
        }
    }

    private void playStage(Scanner scanner, CampaignStage stage) {
        OpponentProfile profile = profileRegistry.findById(stage.opponentProfileId())
                .orElseThrow(() -> new IllegalStateException(
                        "Stage " + stage.stageNumber() + " references unknown profile "
                                + stage.opponentProfileId()));
        BotStrategy bot = botResolver.resolve(profile.strategyName());

        System.out.println();
        System.out.printf("Starting Stage %d: %s%n", stage.stageNumber(), stage.description());
        System.out.println("You face: " + profile.displayName());

        playOneMatch(scanner, stage, bot);
    }

    private void playOneMatch(Scanner scanner, CampaignStage stage, BotStrategy bot) {
        GameState state = factory.newCampaignGame(stage);
        System.out.println();
        if (state.currentTurn() == Player.P1) {
            System.out.println("You start the match.");
        } else {
            System.out.println("Bot starts the match.");
        }

        boolean playerQuit = false;
        while (!state.matchEnded() && !playerQuit) {
            System.out.println();
            System.out.println(boardRenderer.render(state, Player.P1));

            Player current = state.currentTurn();
            if (current == Player.P1) {
                playerQuit = handlePlayerTurn(scanner, state);
            } else {
                handleBotTurn(state, bot);
            }
        }

        if (!playerQuit) {
            printMatchSummary(state);
        }
    }

    private boolean handlePlayerTurn(Scanner scanner, GameState state) {
        System.out.println(handRenderer.render(state.playerState(Player.P1)));
        while (true) {
            System.out.print("Your move (type 'help' for commands) > ");
            String input = readLine(scanner);
            ParseResult result = parser.parse(input, state, Player.P1);
            switch (result) {
                case ParseResult.ParseSuccess success -> {
                    String description = describer.describe(success.move(), state);
                    try {
                        orchestrator.playTurn(state, success.move());
                        System.out.println("You played: " + description);
                        return false;
                    } catch (IllegalMoveException | IllegalStateException ex) {
                        System.out.println("Illegal move: " + ex.getMessage());
                    }
                }
                case ParseResult.ParseError error -> System.out.println("Error: " + error.message());
                case ParseResult.ParseCommand command -> {
                    if (handleCommand(command.commandType(), state)) {
                        return true;
                    }
                }
            }
        }
    }

    private boolean handleCommand(String commandType, GameState state) {
        switch (commandType) {
            case ParseResult.ParseCommand.SHOW_HAND ->
                    System.out.println(handRenderer.render(state.playerState(Player.P1)));
            case ParseResult.ParseCommand.SHOW_BOARD ->
                    System.out.println(boardRenderer.render(state, Player.P1));
            case ParseResult.ParseCommand.SHOW_MOVES -> {
                List<Move> legal = generator.legalMoves(state, Player.P1);
                List<String> formatted = movesFormatter.format(legal, state);
                System.out.println("Legal moves (" + formatted.size() + " distinct, "
                        + legal.size() + " total):");
                formatted.forEach(line -> System.out.println("  - " + line));
            }
            case ParseResult.ParseCommand.SHOW_HELP -> printHelp();
            case ParseResult.ParseCommand.QUIT -> {
                System.out.println("Quitting the match.");
                return true;
            }
            default -> System.out.println("Unknown command: " + commandType);
        }
        return false;
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  play <n>             - play card #n (use index from 'hand')");
        System.out.println("  play <n> <row>       - play horn or similar on close/ranged/siege");
        System.out.println("  play <n> unit-<m>    - play decoy targeting unit #m on your board");
        System.out.println("  pass                 - pass for the rest of the round");
        System.out.println("  leader               - activate your leader ability");
        System.out.println("  hand                 - show your hand again");
        System.out.println("  board                - show the board again");
        System.out.println("  moves                - list all legal moves with descriptions");
        System.out.println("  help                 - this list");
        System.out.println("  quit                 - abandon current match");
    }

    private void handleBotTurn(GameState state, BotStrategy bot) {
        List<Move> legal = generator.legalMoves(state, Player.P2);
        Move chosen = bot.chooseMove(state, Player.P2, legal);
        String description = describer.describe(chosen, state);
        orchestrator.playTurn(state, chosen);
        System.out.println("Bot played: " + description);
        System.out.println();
    }

    private void printMatchSummary(GameState state) {
        System.out.println();
        System.out.println(boardRenderer.render(state, Player.P1));
        Optional<Player> winner = state.matchWinner();
        String winnerLabel = winner.map(p -> p == Player.P1 ? "YOU" : "BOT").orElse("DRAW");
        System.out.println("═══ MATCH ENDED ═══");
        System.out.println("Winner: " + winnerLabel);

        String roundLines = state.roundHistory().stream()
                .map(r -> {
                    String w = r.winner().map(p -> p == Player.P1 ? "YOU" : "BOT").orElse("TIE");
                    return String.format("  R%d: %d-%d (%s)",
                            r.roundNumber(), r.p1Score(), r.p2Score(), w);
                })
                .collect(Collectors.joining(System.lineSeparator()));
        System.out.println("Round results:");
        System.out.println(roundLines);
        System.out.printf("Final: YOU %d — BOT %d%n",
                state.playerState(Player.P1).roundsWon(),
                state.playerState(Player.P2).roundsWon());
    }

    private String readLine(Scanner scanner) {
        if (!scanner.hasNextLine()) {
            return "";
        }
        return scanner.nextLine();
    }
}
