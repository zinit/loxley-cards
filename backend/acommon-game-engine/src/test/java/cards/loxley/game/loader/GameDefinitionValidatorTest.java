package cards.loxley.game.loader;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.Deck;
import cards.loxley.game.domain.card.DeckEntry;
import cards.loxley.game.domain.card.DeckSummary;
import cards.loxley.game.domain.card.GameDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GameDefinitionValidatorTest {

    private static final String REFERENCE_RESOURCE = "data/sherwood_reference_ruleset.json";

    @Autowired
    GameDefinitionLoader loader;

    @Autowired
    GameDefinitionValidator validator;

    private GameDefinition referenceDefinition;

    @BeforeEach
    void loadReference() {
        referenceDefinition = loader.load(REFERENCE_RESOURCE);
    }

    @Test
    void validatesReferenceRulesetWithoutErrors() {
        assertThatCode(() -> validator.validate(referenceDefinition))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDeckSummaryMismatch() {
        DeckSummary tampered = new DeckSummary(
                1,
                referenceDefinition.deck().summary().unitCardCopies() + 5,
                referenceDefinition.deck().summary().specialCardCopies(),
                referenceDefinition.deck().summary().totalPlayableCardCopies()
        );
        GameDefinition broken = withDeck(referenceDefinition, withSummary(referenceDefinition.deck(), tampered));

        assertThatThrownBy(() -> validator.validate(broken))
                .isInstanceOf(GameDefinitionValidationException.class)
                .hasMessageContaining("unitCardCopies")
                .hasMessageContaining("expected 28")
                .hasMessageContaining("was 33");
    }

    @Test
    void rejectsCardWithUnknownAbility() {
        List<Card> mutatedCards = referenceDefinition.cards().stream()
                .map(card -> card.id().equals("sherwood_brothers")
                        ? withAbilities(card, List.of("FAKE_ABILITY"))
                        : card)
                .toList();
        GameDefinition broken = withCards(referenceDefinition, mutatedCards);

        assertThatThrownBy(() -> validator.validate(broken))
                .isInstanceOf(GameDefinitionValidationException.class)
                .hasMessageContaining("sherwood_brothers")
                .hasMessageContaining("FAKE_ABILITY");
    }

    @Test
    void rejectsLeaderCardIdPointingAtUnitCard() {
        Deck tamperedDeck = new Deck(
                referenceDefinition.deck().id(),
                referenceDefinition.deck().name(),
                referenceDefinition.deck().faction(),
                "sherwood_brothers",
                referenceDefinition.deck().cards(),
                referenceDefinition.deck().summary()
        );
        GameDefinition broken = withDeck(referenceDefinition, tamperedDeck);

        Card pointedAt = referenceDefinition.cards().stream()
                .filter(c -> c.id().equals("sherwood_brothers"))
                .findFirst()
                .orElseThrow();
        assertThat(pointedAt.cardType()).isEqualTo(CardType.UNIT);

        assertThatThrownBy(() -> validator.validate(broken))
                .isInstanceOf(GameDefinitionValidationException.class)
                .hasMessageContaining("sherwood_brothers")
                .hasMessageContaining("LEADER")
                .hasMessageContaining("UNIT");
    }

    @Test
    void rejectsDeckEntryReferencingUnknownCard() {
        List<DeckEntry> mutatedEntries = new ArrayList<>(referenceDefinition.deck().cards());
        mutatedEntries.add(new DeckEntry("non_existent_card", 2));
        Deck tamperedDeck = new Deck(
                referenceDefinition.deck().id(),
                referenceDefinition.deck().name(),
                referenceDefinition.deck().faction(),
                referenceDefinition.deck().leaderCardId(),
                mutatedEntries,
                referenceDefinition.deck().summary()
        );
        GameDefinition broken = withDeck(referenceDefinition, tamperedDeck);

        assertThatThrownBy(() -> validator.validate(broken))
                .isInstanceOf(GameDefinitionValidationException.class)
                .hasMessageContaining("non_existent_card");
    }

    private static GameDefinition withDeck(GameDefinition def, Deck deck) {
        return new GameDefinition(
                def.schemaVersion(),
                def.ruleset(),
                def.rows(),
                def.abilities(),
                def.cards(),
                deck
        );
    }

    private static GameDefinition withCards(GameDefinition def, List<Card> cards) {
        return new GameDefinition(
                def.schemaVersion(),
                def.ruleset(),
                def.rows(),
                def.abilities(),
                cards,
                def.deck()
        );
    }

    private static Deck withSummary(Deck deck, DeckSummary summary) {
        return new Deck(
                deck.id(),
                deck.name(),
                deck.faction(),
                deck.leaderCardId(),
                deck.cards(),
                summary
        );
    }

    private static Card withAbilities(Card card, List<String> abilities) {
        return new Card(
                card.id(),
                card.name(),
                card.cardType(),
                card.faction(),
                card.description(),
                card.row(),
                card.basePower(),
                abilities,
                card.playTarget(),
                card.role(),
                card.mvpImportance()
        );
    }
}
