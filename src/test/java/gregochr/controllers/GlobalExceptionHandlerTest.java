package gregochr.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.core.JacksonException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Global Exception Handler Tests")
public class GlobalExceptionHandlerTest {

    @DisplayName("IOException returns 500 with error message")
    @Test
    public void testIOExceptionReturns500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        IOException exception = new IOException("Failed to save properties");

        ResponseEntity<String> response = handler.handleIOException(exception);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Failed to save properties", response.getBody());
    }

    @DisplayName("JacksonException (a malformed Plex payload) returns 500 with the error message")
    @Test
    public void testJacksonExceptionReturns500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        JacksonException exception = new JacksonException("Unrecognized token 'not'") { };

        ResponseEntity<String> response = handler.handleJacksonException(exception);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Unrecognized token 'not'", response.getBody());
    }
}
