package gregochr.services.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.commands.PlexCommand;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexMediaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SSE Event DTO Tests")
public class SseEventDtoTest {

    @DisplayName("DTO captures command event type")
    @Test
    public void testCommandEventType() {
        PlexCommand command = new PlexCommand(PlexEvent.PLAY, "Test Movie", 7200000, PlexMediaType.MOVIE);
        SseEventDto dto = new SseEventDto(command, "{}");

        assertEquals("media.play", dto.command().event());
    }

    @DisplayName("DTO captures command title")
    @Test
    public void testCommandTitle() {
        PlexCommand command = new PlexCommand(PlexEvent.PLAY, "Good Will Hunting", 7200000, PlexMediaType.MOVIE);
        SseEventDto dto = new SseEventDto(command, "{}");

        assertEquals("Good Will Hunting", dto.command().title());
    }

    @DisplayName("DTO captures command duration")
    @Test
    public void testCommandDuration() {
        PlexCommand command = new PlexCommand(PlexEvent.STOP, "Test", 3600000, PlexMediaType.MOVIE);
        SseEventDto dto = new SseEventDto(command, "{}");

        assertEquals(3600000, dto.command().duration());
    }

    @DisplayName("DTO includes timestamp")
    @Test
    public void testTimestampIncluded() {
        PlexCommand command = new PlexCommand(PlexEvent.PLAY, "Test", 1000, PlexMediaType.MOVIE);
        SseEventDto dto = new SseEventDto(command, "{}");

        assertNotNull(dto.timestamp());
        assertTrue(dto.timestamp().contains("T")); // ISO 8601 format
    }

    @DisplayName("DTO serializes payload as raw JSON")
    @Test
    public void testPayloadSerializedAsRawJson() throws JsonProcessingException {
        String rawPayload = "{\"event\":\"media.play\",\"title\":\"Test\",\"librarySectionType\":\"movie\"}";
        PlexCommand command = new PlexCommand(PlexEvent.PLAY, "Test", 1000, PlexMediaType.MOVIE);
        SseEventDto dto = new SseEventDto(command, rawPayload);

        String json = new ObjectMapper().writeValueAsString(dto);

        // Payload should be embedded as raw JSON, not escaped
        assertTrue(json.contains("\"payload\":{\"event\":\"media.play\""));
    }
}
