package cards.loxley.game.engine.opponent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DeckVariantLoaderTest {

    @Autowired
    DeckVariantLoader loader;

    @Test
    void gimpedDeckLoadedWith29CardsAndNoHeroes() {
        DeckVariant variant = loader.findById("gimped_deck").orElseThrow();
        assertThat(variant.totalCardCopies()).isEqualTo(29);
        assertThat(variant.cards())
                .extracting("cardId")
                .doesNotContain("little_john", "will_scarlet", "will_stutely",
                        "fire_arrows", "sherwood_horn", "scarecrow");
    }

    @Test
    void standardDeckLoadedWith37Cards() {
        DeckVariant variant = loader.findById("standard_deck").orElseThrow();
        assertThat(variant.totalCardCopies()).isEqualTo(37);
    }

    @Test
    void boostedDeckLoadedWith38Cards() {
        DeckVariant variant = loader.findById("boosted_deck").orElseThrow();
        assertThat(variant.totalCardCopies()).isEqualTo(38);
        int littleJohnCount = variant.cards().stream()
                .filter(e -> e.cardId().equals("little_john"))
                .mapToInt(e -> e.count())
                .sum();
        assertThat(littleJohnCount).isEqualTo(2);
    }

    @Test
    void boostedPlusDeckLoadedWith39Cards() {
        DeckVariant variant = loader.findById("boosted_plus_deck").orElseThrow();
        assertThat(variant.totalCardCopies()).isEqualTo(39);
        int fireArrowsCount = variant.cards().stream()
                .filter(e -> e.cardId().equals("fire_arrows"))
                .mapToInt(e -> e.count())
                .sum();
        assertThat(fireArrowsCount).isEqualTo(2);
    }
}
