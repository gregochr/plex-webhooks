package gregochr.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

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
}
