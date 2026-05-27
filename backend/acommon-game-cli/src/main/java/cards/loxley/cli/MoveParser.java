package cards.loxley.cli;

import cards.loxley.game.domain.card.Card;
import cards.loxley.game.domain.card.CardType;
import cards.loxley.game.domain.card.PlayTarget;
import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.CardInstance;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.domain.state.PlayerState;
import cards.loxley.game.engine.move.PassMove;
import cards.loxley.game.engine.move.PlayCardMove;
import cards.loxley.game.engine.move.UseLeaderMove;
import cards.loxley.game.engine.scoring.AbilityCodes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class MoveParser {

    public ParseResult parse(String input, GameState state, Player player) {
        String normalized = input == null ? "" : input.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return new ParseResult.ParseError("Empty input. Type 'help' for commands.");
        }

        String[] tokens = normalized.split("\\s+");
        String first = tokens[0];

        return switch (first) {
            case "pass" -> new ParseResult.ParseSuccess(new PassMove(player));
            case "leader" -> new ParseResult.ParseSuccess(new UseLeaderMove(player));
            case "hand" -> new ParseResult.ParseCommand(ParseResult.ParseCommand.SHOW_HAND);
            case "board" -> new ParseResult.ParseCommand(ParseResult.ParseCommand.SHOW_BOARD);
            case "moves" -> new ParseResult.ParseCommand(ParseResult.ParseCommand.SHOW_MOVES);
            case "help" -> new ParseResult.ParseCommand(ParseResult.ParseCommand.SHOW_HELP);
            case "quit", "exit" -> new ParseResult.ParseCommand(ParseResult.ParseCommand.QUIT);
            case "play" -> parsePlay(tokens, state, player);
            default -> new ParseResult.ParseError("Unknown command '" + first + "'. Type 'help'.");
        };
    }

    public List<CardInstance> eligibleDecoyTargets(GameState state, Player player) {
        List<CardInstance> targets = new ArrayList<>();
        PlayerBoardIndex index = PlayerBoardIndex.forPlayer(state, player);
        for (PlayerBoardIndex.IndexedUnit u : index.units()) {
            if (!u.unit().card().abilities().contains(AbilityCodes.HERO)) {
                targets.add(u.unit());
            }
        }
        return targets;
    }

    private ParseResult parsePlay(String[] tokens, GameState state, Player player) {
        if (tokens.length < 2) {
            return new ParseResult.ParseError("Usage: play <card-number> [row|unit-N]");
        }
        Integer cardIndex = parsePositiveInt(tokens[1]);
        if (cardIndex == null) {
            return new ParseResult.ParseError("Card number must be a positive integer.");
        }

        PlayerState ps = state.playerState(player);
        List<CardInstance> hand = ps.hand();
        if (cardIndex < 1 || cardIndex > hand.size()) {
            return new ParseResult.ParseError(
                    "No card #" + cardIndex + " in hand (hand size: " + hand.size() + ").");
        }
        CardInstance handCard = hand.get(cardIndex - 1);
        Card card = handCard.card();

        if (card.cardType() == CardType.UNIT) {
            return parseUnitPlay(tokens, player, handCard);
        }
        return parseSpecialPlay(tokens, state, player, handCard);
    }

    private ParseResult parseUnitPlay(String[] tokens, Player player, CardInstance handCard) {
        Card card = handCard.card();
        boolean isSpy = card.abilities().contains(AbilityCodes.SPY);

        if (tokens.length >= 3) {
            RowId requested = parseRow(tokens[2]);
            if (requested == null) {
                return new ParseResult.ParseError(
                        "Unknown row '" + tokens[2] + "'. Use close/ranged/siege.");
            }
            if (requested != card.row()) {
                return new ParseResult.ParseError(
                        "Card '" + card.name() + "' belongs to row " + card.row()
                                + ", not " + requested + ".");
            }
        }

        if (isSpy) {
            return new ParseResult.ParseSuccess(
                    PlayCardMove.spy(player, handCard.instanceId(), card.row()));
        }
        return new ParseResult.ParseSuccess(
                PlayCardMove.unit(player, handCard.instanceId(), card.row()));
    }

    private ParseResult parseSpecialPlay(String[] tokens, GameState state, Player player, CardInstance handCard) {
        Card card = handCard.card();
        PlayTarget target = card.playTarget();

        if (target == PlayTarget.GLOBAL) {
            if (tokens.length > 2) {
                return new ParseResult.ParseError(
                        "Card '" + card.name() + "' is global and takes no target.");
            }
            return new ParseResult.ParseSuccess(
                    PlayCardMove.special(player, handCard.instanceId()));
        }

        if (target == PlayTarget.SELECTED_ROW) {
            if (tokens.length < 3) {
                return new ParseResult.ParseError(
                        "Card '" + card.name() + "' requires a row. Use: play "
                                + indexLabel(handCard, state.playerState(player))
                                + " close|ranged|siege");
            }
            RowId row = parseRow(tokens[2]);
            if (row == null) {
                return new ParseResult.ParseError(
                        "Unknown row '" + tokens[2] + "'. Use close/ranged/siege.");
            }
            return new ParseResult.ParseSuccess(
                    PlayCardMove.specialOnRow(player, handCard.instanceId(), row));
        }

        if (target == PlayTarget.OWN_UNIT_ON_BOARD) {
            PlayerBoardIndex index = PlayerBoardIndex.forPlayer(state, player);
            List<PlayerBoardIndex.IndexedUnit> eligible = new ArrayList<>();
            for (PlayerBoardIndex.IndexedUnit u : index.units()) {
                if (!u.unit().card().abilities().contains(AbilityCodes.HERO)) {
                    eligible.add(u);
                }
            }
            if (eligible.isEmpty()) {
                return new ParseResult.ParseError(
                        "Decoy needs a unit target but your board has no eligible unit.");
            }
            if (tokens.length < 3) {
                return new ParseResult.ParseError(
                        decoyTargetHelp(handCard, state.playerState(player), eligible));
            }
            Integer unitIndex = parseUnitToken(tokens[2]);
            if (unitIndex == null) {
                return new ParseResult.ParseError(
                        "Expected target like 'unit-<N>' where N is unit number from board display. Got: '"
                                + tokens[2] + "'.");
            }
            Optional<PlayerBoardIndex.IndexedUnit> picked = index.find(unitIndex);
            if (picked.isEmpty()) {
                return new ParseResult.ParseError(
                        "No unit at position " + unitIndex + ". You have " + index.size()
                                + " unit(s) on board. Use 'board' to see them.");
            }
            CardInstance targetUnit = picked.get().unit();
            if (targetUnit.card().abilities().contains(AbilityCodes.HERO)) {
                return new ParseResult.ParseError(
                        "Cannot decoy HERO unit '" + targetUnit.card().name() + "'.");
            }
            return new ParseResult.ParseSuccess(PlayCardMove.specialOnUnit(
                    player, handCard.instanceId(), targetUnit.instanceId()));
        }

        return new ParseResult.ParseError(
                "Cannot play '" + card.name() + "' (unsupported playTarget " + target + ").");
    }

    private String decoyTargetHelp(CardInstance handCard, PlayerState ps,
                                   List<PlayerBoardIndex.IndexedUnit> eligible) {
        StringBuilder sb = new StringBuilder();
        sb.append("Decoy needs a unit target. Use: play ")
                .append(indexLabel(handCard, ps))
                .append(" unit-<N>. Your units on board:");
        for (PlayerBoardIndex.IndexedUnit u : eligible) {
            sb.append(System.lineSeparator())
                    .append("    [u")
                    .append(u.index())
                    .append("] ")
                    .append(u.unit().card().name())
                    .append(" (")
                    .append(u.rowId())
                    .append(")");
        }
        return sb.toString();
    }

    private String indexLabel(CardInstance handCard, PlayerState ps) {
        for (int i = 0; i < ps.hand().size(); i++) {
            if (ps.hand().get(i).instanceId().equals(handCard.instanceId())) {
                return Integer.toString(i + 1);
            }
        }
        return "?";
    }

    private RowId parseRow(String token) {
        return switch (token) {
            case "close" -> RowId.CLOSE;
            case "ranged" -> RowId.RANGED;
            case "siege" -> RowId.SIEGE;
            default -> null;
        };
    }

    private Integer parsePositiveInt(String token) {
        try {
            int v = Integer.parseInt(token);
            return v >= 0 ? v : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseUnitToken(String token) {
        if (!token.startsWith("unit-")) {
            return null;
        }
        return parsePositiveInt(token.substring("unit-".length()));
    }
}
