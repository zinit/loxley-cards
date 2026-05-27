package cards.loxley.game.engine.ability;

public interface AbilityEffect {

    String code();

    void apply(AbilityContext context);
}
