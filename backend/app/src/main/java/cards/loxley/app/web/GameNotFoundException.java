package cards.loxley.app.web;

public class GameNotFoundException extends RuntimeException {

    public GameNotFoundException(String gameId) {
        super("Unknown gameId: " + gameId);
    }
}
