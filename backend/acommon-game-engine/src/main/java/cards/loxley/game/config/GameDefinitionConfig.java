package cards.loxley.game.config;

import cards.loxley.game.domain.card.GameDefinition;
import cards.loxley.game.loader.GameDefinitionLoader;
import cards.loxley.game.loader.GameDefinitionValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameDefinitionConfig {

    public static final String REFERENCE_RULESET_RESOURCE = "data/sherwood_reference_ruleset.json";

    @Bean
    public GameDefinition referenceGameDefinition(
            GameDefinitionLoader loader,
            GameDefinitionValidator validator
    ) {
        GameDefinition def = loader.load(REFERENCE_RULESET_RESOURCE);
        validator.validate(def);
        return def;
    }
}
