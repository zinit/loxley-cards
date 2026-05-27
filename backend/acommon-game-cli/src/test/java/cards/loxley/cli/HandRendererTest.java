package cards.loxley.cli;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.cli.MoveTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HandRendererTest {

    @Autowired
    HandRenderer renderer;

    @Autowired
    GameDefinition definition;

    @Test
    void rendersHandOfMixedCardsWithNumberedRows() {
        Card lady = MoveTestFixtures.cardById(definition, "lady_marian");
        Card brothers = MoveTestFixtures.cardById(definition, "sherwood_brothers");
        Card allan = MoveTestFixtures.cardById(definition, "allan_a_dale");
        Card horn = MoveTestFixtures.cardById(definition, "sherwood_horn");
        Card icyDawn = MoveTestFixtures.cardById(definition, "icy_dawn");
        GameState state = MoveTestFixtures.gameStateWith(
                definition, List.of(lady, brothers, allan, horn, icyDawn), List.of());

        String output = renderer.render(state.playerState(Player.P1));

        assertThat(output).contains("YOUR HAND:");
        assertThat(output).contains("[1] Lady Marian");
        assertThat(output).contains("[2] Bracia z Sherwood");
        assertThat(output).contains("[3] Allan-a-Dale");
        assertThat(output).contains("[4] Róg Sherwoodu");
        assertThat(output).contains("[5] Lodowaty Świt");
        assertThat(output).contains("ability:SPY");
        assertThat(output).contains("target:ROW");
        assertThat(output).contains("target:GLOBAL");
    }

    @Test
    void rendersEmptyHandAsParenthesizedEmpty() {
        GameState state = MoveTestFixtures.gameStateWith(definition, List.of(), List.of());

        String output = renderer.render(state.playerState(Player.P1));

        assertThat(output).contains("YOUR HAND:");
        assertThat(output).contains("(empty)");
    }
}
