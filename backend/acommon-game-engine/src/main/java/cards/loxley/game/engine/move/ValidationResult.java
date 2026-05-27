package cards.loxley.game.engine.move;

public sealed interface ValidationResult permits ValidationResult.Valid, ValidationResult.Invalid {

    record Valid() implements ValidationResult {
    }

    record Invalid(String reason) implements ValidationResult {
    }
}
