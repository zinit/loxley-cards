package cards.loxley.game.domain.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class PlayerState {

    private static final String DEFAULT_FACTION_ID = "SHERWOOD_OUTLAWS";

    private final Player player;
    private final BoardSide board;
    private final CardInstance leader;
    private final String factionId;
    private final List<CardInstance> hand = new ArrayList<>();
    private final List<CardInstance> deck = new ArrayList<>();
    private final List<CardInstance> graveyard = new ArrayList<>();
    private boolean leaderUsed = false;
    private boolean passed = false;
    private int roundsWon = 0;

    public PlayerState(Player player, CardInstance leader, List<CardInstance> initialDeck) {
        this(player, leader, initialDeck, DEFAULT_FACTION_ID);
    }

    public PlayerState(Player player, CardInstance leader, List<CardInstance> initialDeck, String factionId) {
        this.player = player;
        this.leader = leader;
        this.factionId = factionId;
        this.board = new BoardSide();
        this.deck.addAll(initialDeck);
    }

    public Player player() {
        return player;
    }

    public BoardSide board() {
        return board;
    }

    public CardInstance leader() {
        return leader;
    }

    public String factionId() {
        return factionId;
    }

    public List<CardInstance> hand() {
        return Collections.unmodifiableList(hand);
    }

    public List<CardInstance> deck() {
        return Collections.unmodifiableList(deck);
    }

    public List<CardInstance> graveyard() {
        return Collections.unmodifiableList(graveyard);
    }

    public boolean leaderUsed() {
        return leaderUsed;
    }

    public boolean passed() {
        return passed;
    }

    public int roundsWon() {
        return roundsWon;
    }

    public Optional<CardInstance> drawCard() {
        if (deck.isEmpty()) {
            return Optional.empty();
        }
        CardInstance drawn = deck.remove(deck.size() - 1);
        hand.add(drawn);
        return Optional.of(drawn);
    }

    public int drawCards(int count) {
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            if (drawCard().isEmpty()) {
                break;
            }
            drawn++;
        }
        return drawn;
    }

    public boolean removeFromHand(String instanceId) {
        Iterator<CardInstance> it = hand.iterator();
        while (it.hasNext()) {
            if (it.next().instanceId().equals(instanceId)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public void sendToGraveyard(CardInstance ci) {
        graveyard.add(ci);
    }

    public boolean removeFromGraveyard(String instanceId) {
        Iterator<CardInstance> it = graveyard.iterator();
        while (it.hasNext()) {
            if (it.next().instanceId().equals(instanceId)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public void addToHand(CardInstance ci) {
        hand.add(ci);
    }

    public void incrementRoundsWon() {
        roundsWon++;
    }

    public void resetForNewRound() {
        passed = false;
        board.clearAll();
    }

    public void markLeaderUsed() {
        this.leaderUsed = true;
    }

    public void markPassed() {
        this.passed = true;
    }
}
