package cards.loxley.app.web;

import cards.loxley.app.web.dto.CreateGameRequest;
import cards.loxley.app.web.dto.GameStateView;
import cards.loxley.app.web.dto.MoveRequest;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.bot.BotStrategy;
import cards.loxley.game.engine.bot.BotStrategyResolver;
import cards.loxley.game.engine.campaign.CampaignStage;
import cards.loxley.game.engine.campaign.CampaignStageRegistry;
import cards.loxley.game.engine.execution.TurnOrchestrator;
import cards.loxley.game.engine.move.*;
import cards.loxley.game.engine.opponent.OpponentProfile;
import cards.loxley.game.engine.opponent.OpponentProfileRegistry;
import cards.loxley.game.domain.state.GameStateFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameStateFactory factory;
    private final CampaignStageRegistry stageRegistry;
    private final OpponentProfileRegistry profileRegistry;
    private final BotStrategyResolver botResolver;
    private final MoveGenerator generator;
    private final TurnOrchestrator orchestrator;
    private final GameStateMapper mapper;
    private final GameSessionStore sessionStore;

    public GameController(GameStateFactory factory,
                          CampaignStageRegistry stageRegistry,
                          OpponentProfileRegistry profileRegistry,
                          BotStrategyResolver botResolver,
                          MoveGenerator generator,
                          TurnOrchestrator orchestrator,
                          GameStateMapper mapper,
                          GameSessionStore sessionStore) {
        this.factory = factory;
        this.stageRegistry = stageRegistry;
        this.profileRegistry = profileRegistry;
        this.botResolver = botResolver;
        this.generator = generator;
        this.orchestrator = orchestrator;
        this.mapper = mapper;
        this.sessionStore = sessionStore;
    }

    @PostMapping
    public GameStateView createGame(@RequestBody CreateGameRequest request) {
        CampaignStage stage = stageRegistry.findByNumber(request.stageNumber())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown stage: " + request.stageNumber()));
        GameState state = factory.newCampaignGame(stage);
        String gameId = sessionStore.create(state, stage);

        synchronized (sessionStore.lock(gameId)) {
            Move lastBotMove = driveBotMoves(state, gameId);
            List<Move> legalMoves = generator.legalMoves(state, Player.P1);
            return mapper.toView(state, gameId, Player.P1, legalMoves, null, lastBotMove);
        }
    }

    @GetMapping("/{gameId}")
    public GameStateView getState(@PathVariable String gameId) {
        GameState state = sessionStore.findOrThrow(gameId);
        synchronized (sessionStore.lock(gameId)) {
            List<Move> legalMoves = state.matchEnded()
                    ? List.of()
                    : generator.legalMoves(state, Player.P1);
            return mapper.toView(state, gameId, Player.P1, legalMoves);
        }
    }

    @PostMapping("/{gameId}/moves")
    public GameStateView makeMove(@PathVariable String gameId,
                                  @RequestBody MoveRequest request) {
        GameState state = sessionStore.findOrThrow(gameId);
        synchronized (sessionStore.lock(gameId)) {
            Move playerMove = mapMove(request, state);
            try {
                orchestrator.playTurn(state, playerMove);
            } catch (IllegalStateException e) {
                throw new GameStateException(e.getMessage(), e);
            }

            Move lastBotMove = driveBotMoves(state, gameId);

            List<Move> legalMoves = state.matchEnded()
                    ? List.of()
                    : generator.legalMoves(state, Player.P1);
            return mapper.toView(state, gameId, Player.P1, legalMoves,
                    playerMove, lastBotMove);
        }
    }

    private Move driveBotMoves(GameState state, String gameId) {
        Move lastBotMove = null;
        int safety = 0;
        while (!state.matchEnded() && state.currentTurn() == Player.P2) {
            if (++safety > 200) {
                throw new IllegalStateException("Bot loop exceeded 200 iterations — likely engine bug");
            }
            List<Move> legal = generator.legalMoves(state, Player.P2);
            if (legal.isEmpty()) break;
            OpponentProfile profile = profileRegistry.findById(
                    sessionStore.findStageOrThrow(gameId).opponentProfileId()).orElseThrow();
            BotStrategy bot = botResolver.resolve(profile.strategyName());
            lastBotMove = bot.chooseMove(state, Player.P2, legal);
            try {
                orchestrator.playTurn(state, lastBotMove);
            } catch (IllegalStateException e) {
                throw new GameStateException(e.getMessage(), e);
            }
        }
        return lastBotMove;
    }

    private Move mapMove(MoveRequest req, GameState state) {
        if (req.kind() == null) {
            throw new IllegalArgumentException("Move kind is required");
        }
        Player p = Player.P1;
        return switch (req.kind()) {
            case "PASS" -> new PassMove(p);
            case "LEADER" -> new UseLeaderMove(p);
            case "UNIT" -> PlayCardMove.unit(p, req.handInstanceId(),
                    RowId.valueOf(req.targetRow()));
            case "SPY" -> PlayCardMove.spy(p, req.handInstanceId(),
                    RowId.valueOf(req.targetRow()));
            case "SPECIAL" -> PlayCardMove.special(p, req.handInstanceId());
            case "ROW" -> PlayCardMove.specialOnRow(p, req.handInstanceId(),
                    RowId.valueOf(req.targetRow()));
            case "UNIT_TARGET" -> PlayCardMove.specialOnUnit(p, req.handInstanceId(),
                    req.targetInstanceId());
            default -> throw new IllegalArgumentException("Unknown move kind: " + req.kind());
        };
    }
}
