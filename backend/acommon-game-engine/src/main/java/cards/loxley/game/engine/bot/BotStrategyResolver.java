package cards.loxley.game.engine.bot;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class BotStrategyResolver {

    private final Map<String, BotStrategy> byName;

    public BotStrategyResolver(List<BotStrategy> strategies) {
        Map<String, BotStrategy> map = new HashMap<>();
        for (BotStrategy s : strategies) {
            map.put(s.name(), s);
        }
        this.byName = Map.copyOf(map);
    }

    public BotStrategy resolve(String name) {
        if ("random".equals(name)) {
            return new RandomBot(new Random());
        }
        BotStrategy strategy = byName.get(name);
        if (strategy == null) {
            throw new IllegalArgumentException("No bot strategy registered for name: " + name);
        }
        return strategy;
    }
}
