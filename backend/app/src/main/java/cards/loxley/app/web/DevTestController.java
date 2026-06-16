package cards.loxley.app.web;

import cards.loxley.app.web.dto.GameStateView;
import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.campaign.CampaignStage;
import cards.loxley.game.engine.campaign.CampaignStageRegistry;
import cards.loxley.game.domain.state.GameStateFactory;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@Profile("dev")
@RequestMapping("/api/dev")
public class DevTestController {

    private static final Set<String> FORCED_CARD_IDS = Set.of(
            "sherwood_horn", "icy_dawn", "sherwood_fog", "forest_storm"
    );

    private final GameStateFactory factory;
    private final CampaignStageRegistry stageRegistry;
    private final MoveGenerator generator;
    private final GameStateMapper mapper;
    private final GameSessionStore sessionStore;
    private final Map<String, Card> cardsById;

    public DevTestController(GameStateFactory factory,
                             CampaignStageRegistry stageRegistry,
                             MoveGenerator generator,
                             GameStateMapper mapper,
                             GameSessionStore sessionStore,
                             GameDefinition gameDefinition) {
        this.factory = factory;
        this.stageRegistry = stageRegistry;
        this.generator = generator;
        this.mapper = mapper;
        this.sessionStore = sessionStore;
        this.cardsById = gameDefinition.cards().stream()
                .collect(Collectors.toMap(Card::id, c -> c));
    }

    @PostMapping("/games/forced-hand")
    public GameStateView createForcedHandGame() {
        CampaignStage stage = stageRegistry.findByNumber(1)
                .orElseThrow(() -> new IllegalStateException("Stage 1 not found"));
        GameState state = factory.newCampaignGame(stage);
        seedForcedCards(state.playerState(Player.P1));
        String gameId = sessionStore.create(state, stage);
        List<Move> legalMoves = generator.legalMoves(state, Player.P1);
        return mapper.toView(state, gameId, Player.P1, legalMoves, null, null, null);
    }

    private void seedForcedCards(PlayerState ps) {
        for (String cardId : FORCED_CARD_IDS) {
            if (ps.hand().stream().anyMatch(ci -> ci.card().id().equals(cardId))) {
                continue;
            }
            Card card = cardsById.get(cardId);
            if (card != null) {
                ps.addToHand(new CardInstance(card, ps.player()));
            }
        }
    }
}
