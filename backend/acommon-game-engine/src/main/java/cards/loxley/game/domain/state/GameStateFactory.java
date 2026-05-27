package cards.loxley.game.domain.state;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.Deck;
import cards.loxley.game.domain.card.DeckEntry;
import cards.loxley.game.domain.card.DeckSummary;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.engine.campaign.CampaignStage;
import cards.loxley.game.engine.opponent.DeckVariant;
import cards.loxley.game.engine.opponent.DeckVariantLoader;
import cards.loxley.game.engine.opponent.OpponentProfile;
import cards.loxley.game.engine.opponent.OpponentProfileRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class GameStateFactory {

    private static final String DEFAULT_FACTION = "SHERWOOD_OUTLAWS";
    private static final String PLAYER_DECK_VARIANT_ID = "standard_deck";

    private final GameDefinition gameDefinition;
    private final Random defaultRandom;
    private final Map<String, Card> cardsById;
    private final OpponentProfileRegistry opponentProfileRegistry;
    private final DeckVariantLoader deckVariantLoader;

    public GameStateFactory(GameDefinition gameDefinition,
                            Random random,
                            OpponentProfileRegistry opponentProfileRegistry,
                            DeckVariantLoader deckVariantLoader) {
        this.gameDefinition = gameDefinition;
        this.defaultRandom = random;
        this.opponentProfileRegistry = opponentProfileRegistry;
        this.deckVariantLoader = deckVariantLoader;
        Map<String, Card> index = new HashMap<>();
        for (Card card : gameDefinition.cards()) {
            index.put(card.id(), card);
        }
        this.cardsById = index;
    }

    public GameState newGame(Deck deckP1, Deck deckP2) {
        return newGame(deckP1, deckP2, defaultRandom);
    }

    public GameState newGame(Deck deckP1, Deck deckP2, Random rng) {
        PlayerState p1State = buildPlayerState(Player.P1, deckP1, rng);
        PlayerState p2State = buildPlayerState(Player.P2, deckP2, rng);
        Player firstPlayer = rng.nextBoolean() ? Player.P1 : Player.P2;
        return new GameState(p1State, p2State, firstPlayer);
    }

    public GameState newCampaignGame(CampaignStage stage) {
        return newCampaignGame(stage, defaultRandom);
    }

    public GameState newCampaignGame(CampaignStage stage, Random rng) {
        OpponentProfile profile = opponentProfileRegistry.findById(stage.opponentProfileId())
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown opponent profile: " + stage.opponentProfileId()));
        DeckVariant playerVariant = deckVariantLoader.findById(PLAYER_DECK_VARIANT_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Player default deck variant '" + PLAYER_DECK_VARIANT_ID + "' not loaded"));
        DeckVariant opponentVariant = deckVariantLoader.findById(profile.deckVariantId())
                .orElseThrow(() -> new IllegalStateException(
                        "Opponent deck variant '" + profile.deckVariantId() + "' not loaded"));

        return newGame(toDeck(playerVariant), toDeck(opponentVariant), rng);
    }

    public Deck toDeck(DeckVariant variant) {
        int unitCopies = 0;
        int specialCopies = 0;
        for (DeckEntry entry : variant.cards()) {
            Card def = cardsById.get(entry.cardId());
            if (def == null) {
                continue;
            }
            switch (def.cardType()) {
                case UNIT -> unitCopies += entry.count();
                case SPECIAL -> specialCopies += entry.count();
                default -> {
                }
            }
        }
        DeckSummary summary = new DeckSummary(1, unitCopies, specialCopies, unitCopies + specialCopies);
        return new Deck(variant.id(), variant.displayName(), DEFAULT_FACTION,
                variant.leaderCardId(), variant.cards(), summary);
    }

    private PlayerState buildPlayerState(Player player, Deck deck, Random rng) {
        Card leaderCard = cardsById.get(deck.leaderCardId());
        if (leaderCard == null) {
            throw new IllegalStateException(
                    "Leader card '" + deck.leaderCardId() + "' is not present in the game definition");
        }
        CardInstance leaderInstance = new CardInstance(leaderCard, player);

        List<CardInstance> deckInstances = new ArrayList<>();
        for (DeckEntry entry : deck.cards()) {
            Card cardDef = cardsById.get(entry.cardId());
            if (cardDef == null) {
                throw new IllegalStateException(
                        "Card '" + entry.cardId() + "' is not present in the game definition");
            }
            for (int i = 0; i < entry.count(); i++) {
                deckInstances.add(new CardInstance(cardDef, player));
            }
        }
        Collections.shuffle(deckInstances, rng);

        PlayerState state = new PlayerState(player, leaderInstance, deckInstances, deck.faction());
        state.drawCards(gameDefinition.ruleset().matchFormat().startingHandSize());
        return state;
    }
}
