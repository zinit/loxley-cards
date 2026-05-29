package cards.loxley.app.web;

import cards.loxley.app.web.dto.ErrorResponse;
import cards.loxley.game.engine.execution.IllegalMoveException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(GameNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalMoveException.class)
    public ResponseEntity<ErrorResponse> handleIllegalMove(IllegalMoveException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("ILLEGAL_MOVE", ex.getMessage()));
    }

    @ExceptionHandler(GameStateException.class)
    public ResponseEntity<ErrorResponse> handleGameState(GameStateException ex) {
        return ResponseEntity.status(409)
                .body(new ErrorResponse("INVALID_STATE", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }
}
