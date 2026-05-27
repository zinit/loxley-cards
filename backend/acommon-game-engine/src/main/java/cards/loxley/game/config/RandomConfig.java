package cards.loxley.game.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class RandomConfig {

    @Bean
    public Random gameRandom() {
        return new Random();
    }
}
