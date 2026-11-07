package gregochr.services.plex.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Plex DTO Tests")
public class PlexDtoTest {

    @DisplayName("PlexMetadataDto toString lists the title, duration and type")
    @Test
    public void metadataToString() {
        PlexMetadataDto metadata = new PlexMetadataDto("Good Will Hunting", 7620000, "movie");

        assertEquals("[ Title: Good Will Hunting, Duration: 7620000, Type: movie ]", metadata.toString());
    }

    @DisplayName("PlexPayloadDto toString lists the event and the metadata")
    @Test
    public void payloadToString() {
        PlexPayloadDto payload = new PlexPayloadDto("media.play", new PlexMetadataDto("Heat", 10200000, "movie"));

        assertEquals("[ Event: media.play, [ Title: Heat, Duration: 10200000, Type: movie ] ]", payload.toString());
    }

    @DisplayName("PlexPayloadDto toString copes with missing metadata")
    @Test
    public void payloadToStringWithoutMetadata() {
        assertEquals("[ Event: media.stop, null ]", new PlexPayloadDto("media.stop", null).toString());
    }

    @DisplayName("PlexPayloadDto reads the capitalised Metadata object and ignores unknown properties")
    @Test
    public void payloadDeserialisesMetadataAndIgnoresUnknownProperties() throws Exception {
        String json = "{\"event\":\"media.pause\",\"user\":true,"
                + "\"Metadata\":{\"type\":\"episode\",\"title\":\"Pilot\",\"duration\":2700000,\"year\":2008}}";

        PlexPayloadDto payload = new ObjectMapper().readValue(json, PlexPayloadDto.class);

        assertEquals("media.pause", payload.event());
        assertEquals(new PlexMetadataDto("Pilot", 2700000, "episode"), payload.metadata());
    }

    @DisplayName("PlexPayloadDto has no metadata when the payload omits it")
    @Test
    public void payloadWithoutMetadata() throws Exception {
        PlexPayloadDto payload = new ObjectMapper().readValue("{\"event\":\"media.stop\"}", PlexPayloadDto.class);

        assertNull(payload.metadata());
    }
}
