package cards.loxley.game.engine.opponent;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DeckVariantLoader {

    private static final List<String> VARIANT_RESOURCES = List.of(
            "data/decks/sherwood_gimped_deck.json",
            "data/decks/sherwood_standard_deck.json",
            "data/decks/sherwood_boosted_deck.json",
            "data/decks/sherwood_boosted_plus_deck.json"
    );

    private final Map<String, DeckVariant> variantsById;

    public DeckVariantLoader(ObjectMapper objectMapper) {
        ObjectMapper lenient = objectMapper
                .rebuild()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
        Map<String, DeckVariant> map = new LinkedHashMap<>();
        for (String resource : VARIANT_RESOURCES) {
            DeckVariant variant = load(lenient, resource);
            map.put(variant.id(), variant);
        }
        this.variantsById = Map.copyOf(map);
    }

    private DeckVariant load(ObjectMapper mapper, String classpathResource) {
        ClassPathResource resource = new ClassPathResource(classpathResource);
        try (InputStream is = resource.getInputStream()) {
            return mapper.readValue(is, DeckVariant.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load deck variant from " + classpathResource, e);
        }
    }

    public Optional<DeckVariant> findById(String id) {
        return Optional.ofNullable(variantsById.get(id));
    }

    public List<DeckVariant> all() {
        return List.copyOf(variantsById.values());
    }
}
