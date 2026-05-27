package cards.loxley.game.engine.bot;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.BoardSide;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.domain.state.RowState;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.PassMove;
import cards.loxley.game.engine.move.PlayCardMove;
import cards.loxley.game.engine.move.UseLeaderMove;
import cards.loxley.game.engine.scoring.AbilityCodes;
import cards.loxley.game.engine.scoring.BoardScorer;
import cards.loxley.game.engine.scoring.CardScorer;
import cards.loxley.game.engine.scoring.RowScorer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HeuristicMediumBot implements BotStrategy {

    private final CardScorer cardScorer;
    private final RowScorer rowScorer;
    private final BoardScorer boardScorer;

    public HeuristicMediumBot(CardScorer cardScorer, RowScorer rowScorer, BoardScorer boardScorer) {
        this.cardScorer = cardScorer;
        this.rowScorer = rowScorer;
        this.boardScorer = boardScorer;
    }

    @Override
    public String name() {
        return "heuristic-medium";
    }

    @Override
    public Move chooseMove(GameState state, Player player, List<Move> legalMoves) {
        Move bestMove = legalMoves.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (Move m : legalMoves) {
            int score = evaluateMove(state, player, m);
            if (score > bestScore) {
                bestScore = score;
                bestMove = m;
            }
        }
        return bestMove;
    }

    int evaluateMove(GameState state, Player player, Move move) {
        if (move instanceof PassMove) {
            return evaluatePass(state, player);
        }
        if (move instanceof UseLeaderMove) {
            return evaluateLeaderUse(state, player);
        }
        if (move instanceof PlayCardMove pcm) {
            return evaluatePlayCard(state, player, pcm);
        }
        return 0;
    }

    private int evaluatePass(GameState state, Player player) {
        PlayerState ps = state.playerState(player);
        PlayerState opp = state.opponent(player);
        boolean leading = iAmLeading(state, player);
        boolean opponentPassed = opp.passed();
        int myHand = ps.hand().size();
        int oppHand = opp.hand().size();

        if (!leading && opponentPassed) {
            return -1000;
        }
        if (!leading) {
            return -50;
        }
        if (opponentPassed) {
            return 100;
        }

        int strengthDiff = boardScorer.playerStrength(state, player)
                - boardScorer.playerStrength(state, player.opponent());
        int handDiff = myHand - oppHand;
        if (handDiff >= 2 && strengthDiff >= 10) {
            return 30;
        }
        return -10;
    }

    private int evaluateLeaderUse(GameState state, Player player) {
        return weatherActiveOnMyBoard(state, player) ? 30 : -100;
    }

    private int evaluatePlayCard(GameState state, Player player, PlayCardMove move) {
        PlayerState ps = state.playerState(player);
        CardInstance ci = findInHand(ps, move.handInstanceId());
        if (ci == null) {
            return 0;
        }
        Card card = ci.card();

        if (card.cardType() == CardType.SPECIAL) {
            return scoreSpecial(state, player, card, move);
        }
        if (card.cardType() == CardType.UNIT) {
            if (card.abilities().contains(AbilityCodes.SPY)) {
                return scoreSpyUnit(state, player, card);
            }
            return scoreRegularUnit(state, player, card);
        }
        return 0;
    }

    private int scoreSpecial(GameState state, Player player, Card card, PlayCardMove move) {
        if (card.abilities().contains(AbilityCodes.SCORCH)) {
            return scoreScorch(state, player);
        }
        if (card.abilities().contains(AbilityCodes.COMMANDERS_HORN)) {
            return scoreHorn(state, player, move.targetRow());
        }
        if (card.abilities().contains(AbilityCodes.WEATHER_CLOSE)) {
            return scoreWeather(state, player, RowId.CLOSE);
        }
        if (card.abilities().contains(AbilityCodes.WEATHER_RANGED)) {
            return scoreWeather(state, player, RowId.RANGED);
        }
        if (card.abilities().contains(AbilityCodes.WEATHER_SIEGE)) {
            return scoreWeather(state, player, RowId.SIEGE);
        }
        if (card.abilities().contains(AbilityCodes.CLEAR_WEATHER)) {
            return weatherActiveOnMyBoard(state, player) ? 20 : -100;
        }
        if (card.abilities().contains(AbilityCodes.DECOY)) {
            return scoreDecoy(state, player, move);
        }
        return 0;
    }

    private int scoreRegularUnit(GameState state, Player player, Card card) {
        int score = card.basePower();
        if (card.abilities().contains(AbilityCodes.TIGHT_BOND)) {
            RowState row = state.playerState(player).board().row(card.row());
            int sameCount = 0;
            for (CardInstance other : row.units()) {
                if (other.card().id().equals(card.id())) {
                    sameCount++;
                }
            }
            if (sameCount >= 1) {
                score += card.basePower();
            }
        }
        return score;
    }

    private int scoreSpyUnit(GameState state, Player player, Card card) {
        int score = -card.basePower() + 10;
        int oppLead = boardScorer.playerStrength(state, player.opponent())
                - boardScorer.playerStrength(state, player);
        if (oppLead > 20) {
            score -= 5;
        }
        return score;
    }

    private int scoreScorch(GameState state, Player player) {
        int maxStrength = 0;
        for (Player p : Player.values()) {
            BoardSide board = state.playerState(p).board();
            for (RowId rowId : RowId.values()) {
                RowState row = board.row(rowId);
                for (CardInstance ci : row.units()) {
                    if (ci.card().abilities().contains(AbilityCodes.HERO)) {
                        continue;
                    }
                    int s = cardScorer.currentStrength(ci, row);
                    if (s > maxStrength) {
                        maxStrength = s;
                    }
                }
            }
        }
        if (maxStrength <= 0) {
            return -100;
        }
        int oppLoss = 0;
        int myLoss = 0;
        for (Player p : Player.values()) {
            BoardSide board = state.playerState(p).board();
            for (RowId rowId : RowId.values()) {
                RowState row = board.row(rowId);
                for (CardInstance ci : row.units()) {
                    if (ci.card().abilities().contains(AbilityCodes.HERO)) {
                        continue;
                    }
                    int s = cardScorer.currentStrength(ci, row);
                    if (s != maxStrength) {
                        continue;
                    }
                    if (ci.owner() == player) {
                        myLoss += s;
                    } else {
                        oppLoss += s;
                    }
                }
            }
        }
        return oppLoss - myLoss;
    }

    private int scoreHorn(GameState state, Player player, RowId targetRow) {
        if (targetRow == null) {
            return 0;
        }
        return rowScorer.rowStrength(state.playerState(player).board().row(targetRow));
    }

    private int scoreWeather(GameState state, Player player, RowId affectedRow) {
        int myStrength = rowScorer.rowStrength(state.playerState(player).board().row(affectedRow));
        int oppStrength = rowScorer.rowStrength(state.opponent(player).board().row(affectedRow));
        return oppStrength > myStrength + 5 ? 20 : -5;
    }

    private int scoreDecoy(GameState state, Player player, PlayCardMove move) {
        String targetInstanceId = move.targetInstanceId();
        if (targetInstanceId == null) {
            return Integer.MIN_VALUE;
        }
        CardInstance target = findOwnedUnit(state, player, targetInstanceId);
        if (target == null) {
            return Integer.MIN_VALUE;
        }
        Card targetCard = target.card();

        int score = targetCard.basePower() == null ? 0 : targetCard.basePower();
        if (targetCard.abilities().contains(AbilityCodes.MEDIC)) {
            score += 15;
        }
        if (targetCard.abilities().contains(AbilityCodes.SPY)) {
            score += 10;
        }
        if (targetCard.abilities().contains(AbilityCodes.TIGHT_BOND)) {
            score += 8;
        }
        score -= 5;
        return score;
    }

    private CardInstance findOwnedUnit(GameState state, Player player, String instanceId) {
        for (Player p : Player.values()) {
            for (RowId rowId : RowId.values()) {
                for (CardInstance ci : state.playerState(p).board().row(rowId).units()) {
                    if (ci.owner() == player && ci.instanceId().equals(instanceId)) {
                        return ci;
                    }
                }
            }
        }
        return null;
    }

    boolean iAmLeading(GameState state, Player player) {
        return boardScorer.playerStrength(state, player)
                > boardScorer.playerStrength(state, player.opponent());
    }

    boolean weatherActiveOnMyBoard(GameState state, Player player) {
        BoardSide board = state.playerState(player).board();
        for (RowId rowId : RowId.values()) {
            if (board.row(rowId).weatherActive()) {
                return true;
            }
        }
        return false;
    }

    private CardInstance findInHand(PlayerState ps, String instanceId) {
        for (CardInstance ci : ps.hand()) {
            if (ci.instanceId().equals(instanceId)) {
                return ci;
            }
        }
        return null;
    }
}
