package cards.loxley.app.web.dto;

import java.util.List;

public record GameStateView(
        String gameId,
        int roundNumber,
        String currentTurn,
        boolean yourTurn,
        boolean matchEnded,
        String matchWinner,
        PlayerView you,
        OpponentView opponent,
        List<RoundResultView> roundHistory,
        List<MoveView> legalMoves,
        LastMoveView yourLastMove,
        LastMoveView opponentLastMove
) {
}
