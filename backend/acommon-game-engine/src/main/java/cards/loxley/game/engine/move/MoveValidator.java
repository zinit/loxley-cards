package cards.loxley.game.engine.move;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.scoring.AbilityCodes;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MoveValidator {

    public ValidationResult validate(GameState state, Move move) {
        PlayerState ps = state.playerState(move.player());

        return switch (move) {
            case PassMove ignored -> validatePass(ps);
            case UseLeaderMove ignored -> validateLeader(ps);
            case PlayCardMove m -> validatePlayCard(state, ps, m);
        };
    }

    private ValidationResult validatePass(PlayerState ps) {
        if (ps.passed()) {
            return new ValidationResult.Invalid("Player already passed");
        }
        return new ValidationResult.Valid();
    }

    private ValidationResult validateLeader(PlayerState ps) {
        if (ps.passed()) {
            return new ValidationResult.Invalid("Player already passed");
        }
        if (ps.leaderUsed()) {
            return new ValidationResult.Invalid("Leader already used");
        }
        return new ValidationResult.Valid();
    }

    private ValidationResult validatePlayCard(GameState state, PlayerState ps, PlayCardMove move) {
        if (ps.passed()) {
            return new ValidationResult.Invalid("Player already passed");
        }

        Optional<CardInstance> handCard = findInHand(ps, move.handInstanceId());
        if (handCard.isEmpty()) {
            return new ValidationResult.Invalid("Card not in hand: " + move.handInstanceId());
        }

        Card card = handCard.get().card();

        return switch (card.cardType()) {
            case UNIT -> validateUnitPlay(card, move);
            case SPECIAL -> validateSpecialPlay(state, move.player(), card, move);
            case LEADER -> new ValidationResult.Invalid("Leader cards cannot be played from hand");
        };
    }

    private ValidationResult validateUnitPlay(Card card, PlayCardMove move) {
        if (move.targetRow() != card.row()) {
            return new ValidationResult.Invalid(
                    "Unit can only be placed on its row: expected " + card.row()
                            + ", got " + move.targetRow());
        }
        if (move.targetInstanceId() != null) {
            return new ValidationResult.Invalid("Unit play does not accept a target instance");
        }
        return new ValidationResult.Valid();
    }

    private ValidationResult validateSpecialPlay(GameState state, Player player, Card card, PlayCardMove move) {
        switch (card.playTarget()) {
            case GLOBAL:
                if (move.targetRow() != null) {
                    return new ValidationResult.Invalid("This special does not accept a target row");
                }
                if (move.targetInstanceId() != null) {
                    return new ValidationResult.Invalid("This special does not accept a target instance");
                }
                return new ValidationResult.Valid();
            case SELECTED_ROW:
                if (move.targetRow() == null) {
                    return new ValidationResult.Invalid("This special requires a target row");
                }
                if (move.targetInstanceId() != null) {
                    return new ValidationResult.Invalid("This special does not accept a target instance");
                }
                return new ValidationResult.Valid();
            case OWN_UNIT_ON_BOARD:
                if (move.targetInstanceId() == null) {
                    return new ValidationResult.Invalid("Decoy requires a target unit");
                }
                Optional<CardInstance> physical = state.findUnitAnywhere(move.targetInstanceId());
                if (physical.isEmpty()) {
                    return new ValidationResult.Invalid("Target unit not found on the board");
                }
                boolean isOnMyBoardSide = isCardPhysicallyOnPlayerBoard(state, player, move.targetInstanceId());
                boolean isMyCard = physical.get().owner() == player;
                if (!isOnMyBoardSide && !isMyCard) {
                    return new ValidationResult.Invalid(
                            "Decoy can only target cards on your side of the board or your own units anywhere");
                }
                if (physical.get().card().abilities().contains(AbilityCodes.HERO)) {
                    return new ValidationResult.Invalid("Cannot target a Hero unit");
                }
                if (move.targetRow() != null) {
                    return new ValidationResult.Invalid("This special does not accept a target row");
                }
                return new ValidationResult.Valid();
            default:
                return new ValidationResult.Invalid("Unsupported play target for special: " + card.playTarget());
        }
    }

    private Optional<CardInstance> findInHand(PlayerState ps, String instanceId) {
        for (CardInstance ci : ps.hand()) {
            if (ci.instanceId().equals(instanceId)) {
                return Optional.of(ci);
            }
        }
        return Optional.empty();
    }

    private boolean isCardPhysicallyOnPlayerBoard(GameState state, Player player, String instanceId) {
        for (RowId rowId : RowId.values()) {
            if (state.playerState(player).board().row(rowId).findUnit(instanceId).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
