package gregochr.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import tools.jackson.core.JacksonException;

import java.io.IOException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException e) {
        return ResponseEntity.internalServerError().body(e.getMessage());
    }

    /**
     * Jackson 3 signals a malformed payload with an unchecked {@link JacksonException}, which no longer reaches
     * {@link #handleIOException}. Keep the answer a malformed Plex payload always got: 500 with the message.
     */
    @ExceptionHandler(JacksonException.class)
    public ResponseEntity<String> handleJacksonException(JacksonException e) {
        return ResponseEntity.internalServerError().body(e.getMessage());
    }
}
