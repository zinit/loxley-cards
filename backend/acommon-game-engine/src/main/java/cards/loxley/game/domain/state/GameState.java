package cards.loxley.game.domain.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class GameState {

    private final PlayerState p1;
    private final PlayerState p2;
    private Player currentTurn;
    private int roundNumber = 1;
    private boolean matchEnded = false;
    private Player matchWinner;
    private final List<RoundResult> roundHistory = new ArrayList<>();

    public record RoundResult(int roundNumber, int p1Score, int p2Score, Optional<Player> winner) {
    }

    public GameState(PlayerState p1, PlayerState p2, Player currentTurn) {
        this.p1 = p1;
        this.p2 = p2;
        this.currentTurn = currentTurn;
    }

    public PlayerState p1() {
        return p1;
    }

    public PlayerState p2() {
        return p2;
    }

    public Player currentTurn() {
        return currentTurn;
    }

    public int roundNumber() {
        return roundNumber;
    }

    public boolean matchEnded() {
        return matchEnded;
    }

    public Optional<Player> matchWinner() {
        return Optional.ofNullable(matchWinner);
    }

    public List<RoundResult> roundHistory() {
        return Collections.unmodifiableList(roundHistory);
    }

    public PlayerState playerState(Player p) {
        return p == Player.P1 ? p1 : p2;
    }

    public PlayerState opponent(Player p) {
        return playerState(p.opponent());
    }

    public Optional<CardInstance> findUnitAnywhere(String instanceId) {
        Optional<CardInstance> onP1 = p1.board().findUnit(instanceId);
        if (onP1.isPresent()) {
            return onP1;
        }
        return p2.board().findUnit(instanceId);
    }

    public void switchTurn() {
        currentTurn = currentTurn.opponent();
    }

    public boolean bothPlayersPassed() {
        return p1.passed() && p2.passed();
    }

    public void assignTurn(Player player) {
        this.currentTurn = player;
    }

    public void advanceRound() {
        this.roundNumber++;
    }

    public void recordRound(RoundResult result) {
        roundHistory.add(result);
    }

    public void endMatchWith(Player winner) {
        this.matchEnded = true;
        this.matchWinner = winner;
    }

    public void endMatchAsDraw() {
        this.matchEnded = true;
        this.matchWinner = null;
    }
}
