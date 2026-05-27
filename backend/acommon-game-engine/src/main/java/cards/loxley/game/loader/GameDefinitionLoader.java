package cards.loxley.game.loader;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import cards.loxley.game.domain.card.GameDefinition;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

@Component
public class GameDefinitionLoader {

    private final ObjectMapper objectMapper;

    public GameDefinitionLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper
                .rebuild()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
    }

    public GameDefinition load(String classpathResource) {
        ClassPathResource resource = new ClassPathResource(classpathResource);
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, GameDefinition.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load game definition from " + classpathResource, e);
        }
    }
}
