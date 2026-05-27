package cards.loxley.game.domain.state;

import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.engine.campaign.CampaignStage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GameStateFactoryTest {

    @Autowired
    GameStateFactory factory;

    @Autowired
    GameDefinition definition;

    @Test
    void newGameSetsUpPlayer1Correctly() {
        GameState state = factory.newGame(definition.deck(), definition.deck());
        PlayerState p1 = state.playerState(Player.P1);

        assertThat(p1.player()).isEqualTo(Player.P1);
        assertThat(p1.hand()).hasSize(definition.ruleset().matchFormat().startingHandSize());
        assertThat(p1.hand().size() + p1.deck().size()).isEqualTo(37);
        assertThat(p1.leader()).isNotNull();
        assertThat(p1.leader().card().cardType()).isEqualTo(CardType.LEADER);
        assertThat(p1.roundsWon()).isZero();
        assertThat(p1.passed()).isFalse();
        assertThat(p1.leaderUsed()).isFalse();
        assertThat(p1.graveyard()).isEmpty();
    }

    @Test
    void newGameSetsUpPlayer2Correctly() {
        GameState state = factory.newGame(definition.deck(), definition.deck());
        PlayerState p2 = state.playerState(Player.P2);

        assertThat(p2.player()).isEqualTo(Player.P2);
        assertThat(p2.hand()).hasSize(definition.ruleset().matchFormat().startingHandSize());
        assertThat(p2.hand().size() + p2.deck().size()).isEqualTo(37);
        assertThat(p2.leader()).isNotNull();
        assertThat(p2.leader().card().cardType()).isEqualTo(CardType.LEADER);
        assertThat(p2.roundsWon()).isZero();
        assertThat(p2.passed()).isFalse();
        assertThat(p2.leaderUsed()).isFalse();
        assertThat(p2.graveyard()).isEmpty();
    }

    @Test
    void newGameInitializesGlobalStateCorrectly() {
        GameState state = factory.newGame(definition.deck(), definition.deck());

        assertThat(state.currentTurn()).isIn(Player.P1, Player.P2);
        assertThat(state.roundNumber()).isEqualTo(1);
        assertThat(state.matchEnded()).isFalse();
        assertThat(state.matchWinner()).isEmpty();
        assertThat(state.roundHistory()).isEmpty();
    }

    @Test
    void p1AndP2HaveDistinctCardInstancesEvenWithSameDeckDefinition() {
        GameState state = factory.newGame(definition.deck(), definition.deck());
        PlayerState p1 = state.playerState(Player.P1);
        PlayerState p2 = state.playerState(Player.P2);

        Set<String> p1Ids = new HashSet<>();
        p1.hand().forEach(ci -> p1Ids.add(ci.instanceId()));
        p1.deck().forEach(ci -> p1Ids.add(ci.instanceId()));
        p1Ids.add(p1.leader().instanceId());

        Set<String> p2Ids = new HashSet<>();
        p2.hand().forEach(ci -> p2Ids.add(ci.instanceId()));
        p2.deck().forEach(ci -> p2Ids.add(ci.instanceId()));
        p2Ids.add(p2.leader().instanceId());

        assertThat(p1Ids).doesNotContainAnyElementsOf(p2Ids);
        assertThat(p1Ids).hasSize(37 + 1);
        assertThat(p2Ids).hasSize(37 + 1);

        assertThat(p1.leader().owner()).isEqualTo(Player.P1);
        assertThat(p2.leader().owner()).isEqualTo(Player.P2);
    }

    @Test
    void newCampaignGameStageOneGivesPlayerStandardAndOpponentGimped() {
        CampaignStage stage = new CampaignStage(1, "ultra_easy", "Lone bandit apprentice");
        GameState state = factory.newCampaignGame(stage);

        int handSize = definition.ruleset().matchFormat().startingHandSize();
        PlayerState p1 = state.playerState(Player.P1);
        PlayerState p2 = state.playerState(Player.P2);

        // P1 = player (standard_deck, 37 total)
        assertThat(p1.hand().size() + p1.deck().size()).isEqualTo(37);
        assertThat(p1.hand()).hasSize(handSize);
        // P2 = opponent (ultra_easy → gimped_deck, 29 total)
        assertThat(p2.hand().size() + p2.deck().size()).isEqualTo(29);
        assertThat(p2.hand()).hasSize(handSize);
    }
}
