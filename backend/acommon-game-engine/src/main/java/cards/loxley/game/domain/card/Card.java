package cards.loxley.game.domain.card;

import java.util.List;

public record Card(
        String id,
        String name,
        CardType cardType,
        String faction,
        String description,
        RowId row,
        Integer basePower,
        List<String> abilities,
        PlayTarget playTarget,
        String role,
        MvpImportance mvpImportance
) {
}
