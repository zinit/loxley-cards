package cards.loxley.game.engine.opponent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OpponentProfileRegistry {

    private final Map<String, OpponentProfile> profilesById;
    private final List<OpponentProfile> orderedProfiles;

    public OpponentProfileRegistry() {
        List<OpponentProfile> ordered = new ArrayList<>();
        ordered.add(new OpponentProfile(
                "ultra_easy",
                "Sherwood Bandit Apprentice",
                "random",
                "gimped_deck",
                "Barely knows which end of the sword to hold."
        ));
        ordered.add(new OpponentProfile(
                "easy",
                "Forest Outlaw",
                "random",
                "gimped_deck",
                "Reckless but armed with a thin deck."
        ));
        ordered.add(new OpponentProfile(
                "medium",
                "Sheriff's Ranger",
                "heuristic-easy",
                "standard_deck",
                "Plays smart units and avoids obvious blunders."
        ));
        ordered.add(new OpponentProfile(
                "hard",
                "Knight of Nottingham",
                "heuristic-medium",
                "boosted_deck",
                "Optimized heuristics and a stacked roster."
        ));
        ordered.add(new OpponentProfile(
                "top_hard",
                "The Sheriff of Nottingham",
                "heuristic-medium",
                "boosted_plus_deck",
                "Twice as many heroes, twice as much fire."
        ));
        Map<String, OpponentProfile> map = new HashMap<>();
        for (OpponentProfile p : ordered) {
            map.put(p.id(), p);
        }
        this.orderedProfiles = List.copyOf(ordered);
        this.profilesById = Map.copyOf(map);
    }

    public Optional<OpponentProfile> findById(String id) {
        return Optional.ofNullable(profilesById.get(id));
    }

    public List<OpponentProfile> all() {
        return orderedProfiles;
    }
}
