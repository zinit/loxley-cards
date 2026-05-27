package cards.loxley.game.engine.execution;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.PlayTarget;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.ability.AbilityContext;
import cards.loxley.game.engine.ability.AbilityRegistry;
import cards.loxley.game.engine.move.Move;
import cards.loxley.game.engine.move.MoveValidator;
import cards.loxley.game.engine.move.PassMove;
import cards.loxley.game.engine.move.PlayCardMove;
import cards.loxley.game.engine.move.UseLeaderMove;
import cards.loxley.game.engine.move.ValidationResult;
import cards.loxley.game.engine.scoring.AbilityCodes;
import org.springframework.stereotype.Component;

@Component
public class MoveExecutor {

    private final MoveValidator validator;
    private final AbilityRegistry abilityRegistry;

    public MoveExecutor(MoveValidator validator, AbilityRegistry abilityRegistry) {
        this.validator = validator;
        this.abilityRegistry = abilityRegistry;
    }

    public void execute(GameState state, Move move) {
        ValidationResult result = validator.validate(state, move);
        if (result instanceof ValidationResult.Invalid invalid) {
            throw new IllegalMoveException(invalid.reason());
        }

        switch (move) {
            case PassMove m -> executePass(state, m);
            case UseLeaderMove m -> executeUseLeader(state, m);
            case PlayCardMove m -> executePlayCard(state, m);
        }
    }

    private void executePass(GameState state, PassMove move) {
        state.playerState(move.player()).markPassed();
    }

    private void executeUseLeader(GameState state, UseLeaderMove move) {
        PlayerState ps = state.playerState(move.player());
        ps.markLeaderUsed();
        AbilityContext ctx = new AbilityContext(state, move.player(), null, null, abilityRegistry);
        abilityRegistry.applyAll(ps.leader().card().abilities(), ctx);
    }

    private void executePlayCard(GameState state, PlayCardMove move) {
        PlayerState ps = state.playerState(move.player());
        CardInstance ci = ps.hand().stream()
                .filter(c -> c.instanceId().equals(move.handInstanceId()))
                .findFirst()
                .orElseThrow(() -> new IllegalMoveException(
                        "Card not in hand: " + move.handInstanceId()));
        ps.removeFromHand(move.handInstanceId());

        Card card = ci.card();
        switch (card.cardType()) {
            case UNIT -> executeUnitPlay(state, move, ci);
            case SPECIAL -> executeSpecialPlay(state, move, ci, ps);
            case LEADER -> throw new IllegalMoveException(
                    "Leader cards cannot be played from hand");
        }

        triggerOnPlayAbilities(state, move, ci);
    }

    private void executeUnitPlay(GameState state, PlayCardMove move, CardInstance ci) {
        boolean isSpy = ci.card().abilities().contains(AbilityCodes.SPY);
        if (isSpy) {
            state.opponent(move.player()).board().row(move.targetRow()).addUnit(ci);
        } else {
            state.playerState(move.player()).board().row(move.targetRow()).addUnit(ci);
        }
    }

    private void executeSpecialPlay(GameState state, PlayCardMove move, CardInstance specialCard, PlayerState ps) {
        ps.sendToGraveyard(specialCard);

        if (specialCard.card().playTarget() == PlayTarget.OWN_UNIT_ON_BOARD) {
            CardInstance original = state.findUnitAnywhere(move.targetInstanceId())
                    .orElseThrow(() -> new IllegalMoveException(
                            "Decoy target not found: " + move.targetInstanceId()));
            // Remove the physical instance wherever it lives — own side or opponent's side.
            for (Player side : Player.values()) {
                if (state.playerState(side).board().removeUnit(move.targetInstanceId())) {
                    break;
                }
            }
            // Decoy transfers ownership to the player who played it. Fresh instanceId so any
            // on-play ability (e.g. SPY) retriggers when the card is replayed from hand.
            CardInstance recycled = new CardInstance(original.card(), move.player());
            ps.addToHand(recycled);
        }
    }

    private void triggerOnPlayAbilities(GameState state, PlayCardMove move, CardInstance card) {
        AbilityContext ctx = new AbilityContext(state, move.player(), card, move, abilityRegistry);
        abilityRegistry.applyAll(card.card().abilities(), ctx);
    }
}
