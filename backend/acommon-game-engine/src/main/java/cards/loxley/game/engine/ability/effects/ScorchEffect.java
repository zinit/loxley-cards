package cards.loxley.game.engine.ability.effects;

import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.BoardSide;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.RowState;
import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.ability.AbilityEffect;
import cards.loxley.game.engine.scoring.AbilityCodes;
import cards.loxley.game.engine.scoring.CardScorer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ScorchEffect implements AbilityEffect {

    private final CardScorer cardScorer;

    public ScorchEffect(CardScorer cardScorer) {
        this.cardScorer = cardScorer;
    }

    @Override
    public String code() {
        return AbilityCodes.SCORCH;
    }

    @Override
    public void apply(AbilityContext ctx) {
        List<UnitOnBoard> snapshot = snapshotNonHeroUnits(ctx);
        if (snapshot.isEmpty()) {
            return;
        }

        int maxStrength = snapshot.stream()
                .mapToInt(UnitOnBoard::strength)
                .max()
                .orElse(0);
        if (maxStrength <= 0) {
            return;
        }

        for (UnitOnBoard doomed : snapshot) {
            if (doomed.strength() == maxStrength) {
                doomed.row().removeUnit(doomed.unit().instanceId());
                ctx.state().playerState(doomed.unit().owner()).sendToGraveyard(doomed.unit());
            }
        }
    }

    private List<UnitOnBoard> snapshotNonHeroUnits(AbilityContext ctx) {
        List<UnitOnBoard> all = new ArrayList<>();
        for (Player p : Player.values()) {
            BoardSide board = ctx.state().playerState(p).board();
            for (RowId rowId : RowId.values()) {
                RowState row = board.row(rowId);
                for (CardInstance ci : row.units()) {
                    if (ci.card().abilities().contains(AbilityCodes.HERO)) {
                        continue;
                    }
                    int strength = cardScorer.currentStrength(ci, row);
                    all.add(new UnitOnBoard(ci, strength, row));
                }
            }
        }
        return all;
    }

    private record UnitOnBoard(CardInstance unit, int strength, RowState row) {
    }
}
