package cards.loxley.cli;

import cards.loxley.game.engine.move.Move;

public sealed interface ParseResult permits
        ParseResult.ParseSuccess,
        ParseResult.ParseError,
        ParseResult.ParseCommand {

    record ParseSuccess(Move move) implements ParseResult {
    }

    record ParseError(String message) implements ParseResult {
    }

    record ParseCommand(String commandType) implements ParseResult {

        public static final String SHOW_HAND = "show_hand";
        public static final String SHOW_BOARD = "show_board";
        public static final String SHOW_MOVES = "show_moves";
        public static final String SHOW_HELP = "show_help";
        public static final String QUIT = "quit";
    }
}
