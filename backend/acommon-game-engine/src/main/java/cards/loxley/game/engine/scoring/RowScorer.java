package cards.loxley.game.engine.scoring;

import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.RowState;
import org.springframework.stereotype.Component;

@Component
public class RowScorer {

    private final CardScorer cardScorer;

    public RowScorer(CardScorer cardScorer) {
        this.cardScorer = cardScorer;
    }

    public int rowStrength(RowState row) {
        int total = 0;
        for (CardInstance card : row.units()) {
            total += cardScorer.currentStrength(card, row);
        }
        return total;
    }
}
