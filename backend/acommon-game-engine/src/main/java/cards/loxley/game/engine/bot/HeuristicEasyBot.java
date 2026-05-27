package cards.loxley.game.engine.bot;

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

import java.util.ArrayList;
import java.util.List;

@Component
public class HeuristicEasyBot implements BotStrategy {

    private static final int SCORCH_THREAT_THRESHOLD = 8;
    private static final int LOW_HAND_THRESHOLD = 4;
    private static final int DECOY_TARGET_MIN_BASE_POWER = 4;

    private final CardScorer cardScorer;
    private final RowScorer rowScorer;
    private final BoardScorer boardScorer;

    public HeuristicEasyBot(CardScorer cardScorer, RowScorer rowScorer, BoardScorer boardScorer) {
        this.cardScorer = cardScorer;
        this.rowScorer = rowScorer;
        this.boardScorer = boardScorer;
    }

    @Override
    public String name() {
        return "heuristic-easy";
    }

    @Override
    public Move chooseMove(GameState state, Player player, List<Move> legalMoves) {
        PlayerState ps = state.playerState(player);
        Player opponent = player.opponent();
        boolean opponentPassed = state.playerState(opponent).passed();
        int myStrength = boardScorer.playerStrength(state, player);
        int oppStrength = boardScorer.playerStrength(state, opponent);
        boolean iAmLeading = myStrength > oppStrength;

        // Rule 1: pass when ahead and opponent has already passed.
        if (opponentPassed && iAmLeading) {
            return firstPassOr(legalMoves);
        }

        // Rule 2: Scorch if opponent has a strong non-hero target.
        if (opponentHasStrongNonHero(state, opponent)) {
            Move scorch = findScorchMove(legalMoves, ps);
            if (scorch != null) {
                return scorch;
            }
        }

        // Rule 3: lazy pass on a thin hand (rarer than before — 1-in-3 instead of 1-in-2).
        if (ps.hand().size() < LOW_HAND_THRESHOLD && ps.hand().size() % 3 == 0) {
            return firstPassOr(legalMoves);
        }

        // Rules 4-6: drop obviously-bad specials from candidates.
        List<Move> candidates = new ArrayList<>(legalMoves);
        candidates.removeIf(m -> isUselessClearWeather(m, ps, state));
        candidates.removeIf(m -> isLowValueDecoy(m, ps, state, player));
        candidates.removeIf(m -> isSelfHarmWeather(m, ps, state, player));

        // Rule 7: prefer UNIT over SPECIAL among remaining play moves.
        Move firstUnit = null;
        Move firstSpecialPlay = null;
        Move firstOtherNonPass = null;
        for (Move m : candidates) {
            if (m instanceof PassMove) {
                continue;
            }
            if (m instanceof UseLeaderMove) {
                if (firstOtherNonPass == null) firstOtherNonPass = m;
                continue;
            }
            if (m instanceof PlayCardMove pcm) {
                CardInstance ci = handCard(ps, pcm.handInstanceId());
                if (ci == null) {
                    continue;
                }
                CardType type = ci.card().cardType();
                if (type == CardType.UNIT && firstUnit == null) {
                    firstUnit = m;
                } else if (type == CardType.SPECIAL && firstSpecialPlay == null) {
                    firstSpecialPlay = m;
                }
            }
        }
        if (firstUnit != null) return firstUnit;
        if (firstSpecialPlay != null) return firstSpecialPlay;
        if (firstOtherNonPass != null) return firstOtherNonPass;

        return firstPassOr(legalMoves);
    }

    private Move firstPassOr(List<Move> legalMoves) {
        for (Move m : legalMoves) {
            if (m instanceof PassMove) {
                return m;
            }
        }
        return legalMoves.get(0);
    }

    private boolean opponentHasStrongNonHero(GameState state, Player opponent) {
        BoardSide board = state.playerState(opponent).board();
        for (RowId rowId : RowId.values()) {
            RowState row = board.row(rowId);
            for (CardInstance ci : row.units()) {
                if (ci.card().abilities().contains(AbilityCodes.HERO)) {
                    continue;
                }
                if (cardScorer.currentStrength(ci, row) >= SCORCH_THREAT_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }

    private Move findScorchMove(List<Move> legalMoves, PlayerState ps) {
        for (Move m : legalMoves) {
            if (!(m instanceof PlayCardMove pcm)) {
                continue;
            }
            CardInstance ci = handCard(ps, pcm.handInstanceId());
            if (ci != null && ci.card().abilities().contains(AbilityCodes.SCORCH)) {
                return pcm;
            }
        }
        return null;
    }

    private boolean isUselessClearWeather(Move m, PlayerState ps, GameState state) {
        if (!(m instanceof PlayCardMove pcm)) return false;
        CardInstance ci = handCard(ps, pcm.handInstanceId());
        if (ci == null || !ci.card().abilities().contains(AbilityCodes.CLEAR_WEATHER)) {
            return false;
        }
        return !anyWeatherActive(state);
    }

    private boolean anyWeatherActive(GameState state) {
        for (Player p : Player.values()) {
            BoardSide board = state.playerState(p).board();
            for (RowId rowId : RowId.values()) {
                if (board.row(rowId).weatherActive()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLowValueDecoy(Move m, PlayerState ps, GameState state, Player player) {
        if (!(m instanceof PlayCardMove pcm)) return false;
        CardInstance ci = handCard(ps, pcm.handInstanceId());
        if (ci == null || !ci.card().abilities().contains(AbilityCodes.DECOY)) {
            return false;
        }
        String targetId = pcm.targetInstanceId();
        if (targetId == null) return false;
        // After Krok 17 decoy can legally target opponent-owned cards (e.g. their spy on my board).
        // Look up by instanceId without owner filter so the basePower rule applies to ALL legal targets.
        CardInstance target = state.findUnitAnywhere(targetId).orElse(null);
        if (target == null) return false;
        Integer basePower = target.card().basePower();
        return basePower == null || basePower < DECOY_TARGET_MIN_BASE_POWER;
    }

    private boolean isSelfHarmWeather(Move m, PlayerState ps, GameState state, Player player) {
        if (!(m instanceof PlayCardMove pcm)) return false;
        CardInstance ci = handCard(ps, pcm.handInstanceId());
        if (ci == null) return false;
        RowId weatherRow = weatherRowFor(ci);
        if (weatherRow == null) return false;
        int myRow = rowScorer.rowStrength(state.playerState(player).board().row(weatherRow));
        int oppRow = rowScorer.rowStrength(state.playerState(player.opponent()).board().row(weatherRow));
        return myRow > oppRow;
    }

    private RowId weatherRowFor(CardInstance ci) {
        List<String> abilities = ci.card().abilities();
        if (abilities.contains(AbilityCodes.WEATHER_CLOSE)) return RowId.CLOSE;
        if (abilities.contains(AbilityCodes.WEATHER_RANGED)) return RowId.RANGED;
        if (abilities.contains(AbilityCodes.WEATHER_SIEGE)) return RowId.SIEGE;
        return null;
    }

    private CardInstance handCard(PlayerState ps, String instanceId) {
        for (CardInstance ci : ps.hand()) {
            if (ci.instanceId().equals(instanceId)) {
                return ci;
            }
        }
        return null;
    }
}
