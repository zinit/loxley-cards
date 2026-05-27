package cards.loxley.cli;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.cli.MoveTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BoardRendererTest {

    @Autowired
    BoardRenderer renderer;

    @Autowired
    GameDefinition definition;

    @Test
    void emptyBoardShowsDashPlaceholder() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());

        String output = renderer.render(state, Player.P1);

        assertThat(output).contains("CLOSE").contains("RANGED").contains("SIEGE");
        assertThat(output).contains("—");
        assertThat(output).contains("YOU").contains("BOT");
    }

    @Test
    void rowWithHornShowsTagBeforeStrength() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P1).board().close().applyHorn();

        String output = renderer.render(state, Player.P1);

        assertThat(output).contains("[H]");
        // Two Lady Marians under Horn: each base 5 doubled = 10 each → row strength 20.
        assertThat(output).contains("= 20");
    }

    @Test
    void rowWithWeatherShowsWeatherTag() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        Card much = MoveTestFixtures.cardById(definition, "much_millers_son");
        state.playerState(Player.P1).board().ranged().addUnit(new CardInstance(much, Player.P1));
        state.playerState(Player.P1).board().ranged().applyWeather();

        String output = renderer.render(state, Player.P1);

        assertThat(output).contains("[W]");
        // Much (basePower 6) under weather → 1.
        assertThat(output).contains("RANGED");
    }

    @Test
    void playerUnitsGetNumberedTagsInDisplayOrder() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card richard = MoveTestFixtures.cardById(definition, "sir_richard_lea");
        Card trebuchet = MoveTestFixtures.cardById(definition, "sherwood_trebuchet");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(richard, Player.P1));
        state.playerState(Player.P1).board().close().addUnit(new CardInstance(lady, Player.P1));
        state.playerState(Player.P1).board().siege().addUnit(new CardInstance(trebuchet, Player.P1));

        String output = renderer.render(state, Player.P1);

        assertThat(output).contains("Sir Richard z Lea(5)[u1]");
        assertThat(output).contains("Lady Marian(5)[u2]");
        assertThat(output).contains("Trebusz Sherwoodu(8)[u3]");
    }

    @Test
    void botUnitsDoNotGetNumberedTagsUnlessOwnedByPlayer() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card allan = MoveTestFixtures.cardById(definition, "allan_a_dale");
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());
        // Bot's own card on bot's board: no [u] tag.
        state.playerState(Player.P2).board().close().addUnit(new CardInstance(lady, Player.P2));
        // Player's spy physically on bot's board: SHOULD have [u] tag.
        CardInstance spyOnBot = new CardInstance(allan, Player.P1);
        state.playerState(Player.P2).board().close().addUnit(spyOnBot);

        String output = renderer.render(state, Player.P1);

        // Bot-owned Lady has no [u] tag (it's directly followed by a space or end-of-line, not [u).
        assertThat(output).doesNotContain("Lady Marian(5)[u");
        // P1's spy physically on bot's board IS tagged with [u1, mine] — owner=P1 but lives on P2 side.
        assertThat(output).contains("Allan-a-Dale(4)[u1, mine]");
    }
}
