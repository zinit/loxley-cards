package cards.loxley.game.domain.state;

public final class PlayerStateTestSupport {

    private PlayerStateTestSupport() {
    }

    public static void markLeaderUsed(PlayerState ps) {
        ps.markLeaderUsed();
    }

    public static void markPassed(PlayerState ps) {
        ps.markPassed();
    }
}
