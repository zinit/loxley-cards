package cards.loxley.app.web;

import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.engine.campaign.CampaignStage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameSessionStore {

    private final Map<String, GameState> games = new ConcurrentHashMap<>();
    private final Map<String, CampaignStage> stages = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAccess = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public String create(GameState state, CampaignStage stage) {
        String id = UUID.randomUUID().toString();
        games.put(id, state);
        stages.put(id, stage);
        lastAccess.put(id, System.currentTimeMillis());
        return id;
    }

    public GameState findOrThrow(String gameId) {
        GameState state = games.get(gameId);
        if (state == null) {
            throw new GameNotFoundException(gameId);
        }
        lastAccess.put(gameId, System.currentTimeMillis());
        return state;
    }

    public CampaignStage findStageOrThrow(String gameId) {
        CampaignStage stage = stages.get(gameId);
        if (stage == null) {
            throw new GameNotFoundException(gameId);
        }
        return stage;
    }

    public Object lock(String gameId) {
        return locks.computeIfAbsent(gameId, k -> new Object());
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanupStale() {
        long cutoff = System.currentTimeMillis() - 30 * 60_000;
        lastAccess.entrySet().removeIf(e -> {
            if (e.getValue() < cutoff) {
                String id = e.getKey();
                games.remove(id);
                stages.remove(id);
                locks.remove(id);
                return true;
            }
            return false;
        });
    }
}
