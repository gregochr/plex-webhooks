package gregochr.services.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.commands.PlexCommand;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexMediaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SSE Event DTO Tests")
public class SseEventDtoTest {

    @DisplayName("DTO captures the command event")
    @Test
    public void testCommandEventType() {
        PlexCommand command = new PlexCommand(PlexEvent.PLAY, "Test Movie", 7200000, PlexMediaType.MOVIE);

        assertEquals("media.play", new SseEventDto(command, "{}", FIXED).command().event());
    }

    @DisplayName("DTO captures the command title")
    @Test
    public void testCommandTitle() {
        PlexCommand command = new PlexCommand(PlexEvent.PLAY, "Good Will Hunting", 7200000, PlexMediaType.MOVIE);

        assertEquals("Good Will Hunting", new SseEventDto(command, "{}", FIXED).command().title());
    }

    @DisplayName("DTO captures the command duration")
    @Test
    public void testCommandDuration() {
        PlexCommand command = new PlexCommand(PlexEvent.STOP, "Test", 3600000, PlexMediaType.MOVIE);

        assertEquals(3600000, new SseEventDto(command, "{}", FIXED).command().duration());
    }

    @DisplayName("DTO timestamp is the injected clock's instant in ISO 8601")
    @Test
    public void testTimestampFromClock() {
        PlexCommand command = new PlexCommand(PlexEvent.PLAY, "Test", 1000, PlexMediaType.MOVIE);

        assertEquals("2024-03-15T12:00:00Z", new SseEventDto(command, "{}", FIXED).timestamp());
    }

    @DisplayName("DTO keeps the raw payload string")
    @Test
    public void testPayloadKept() {
        PlexCommand command = new PlexCommand(PlexEvent.PLAY, "Test", 1000, PlexMediaType.MOVIE);

        assertEquals("{\"a\":1}", new SseEventDto(command, "{\"a\":1}", FIXED).payload());
    }

    @DisplayName("DTO serialises the payload as raw JSON, not an escaped string")
    @Test
    public void testPayloadSerializedAsRawJson() throws JsonProcessingException {
        String rawPayload = "{\"event\":\"media.play\",\"title\":\"Test\",\"librarySectionType\":\"movie\"}";
        PlexCommand command = new PlexCommand(PlexEvent.PLAY, "Test", 1000, PlexMediaType.MOVIE);

        String json = new ObjectMapper().writeValueAsString(new SseEventDto(command, rawPayload, FIXED));

        assertEquals("{\"command\":{\"event\":\"media.play\",\"title\":\"Test\",\"duration\":1000},"
                + "\"payload\":" + rawPayload + ",\"timestamp\":\"2024-03-15T12:00:00Z\"}", json);
    }
}
