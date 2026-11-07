package gregochr.services.plex.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

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

        PlexPayloadDto payload = new JsonMapper().readValue(json, PlexPayloadDto.class);

        assertEquals("media.pause", payload.event());
        assertEquals(new PlexMetadataDto("Pilot", 2700000, "episode", null, null, null, 2008), payload.metadata());
        assertNull(payload.player());
    }

    @DisplayName("PlexPayloadDto reads the season, episode, show, year and the player's device name only")
    @Test
    public void payloadReadsEpisodeDetailsAndPlayerTitle() throws Exception {
        String json = "{\"event\":\"media.play\",\"Player\":{\"title\":\"Den TV\","
                + "\"publicAddress\":\"203.0.113.9\",\"uuid\":\"u\"},\"Metadata\":{\"type\":\"episode\","
                + "\"title\":\"Cold Open\",\"grandparentTitle\":\"Slow Horses\",\"parentIndex\":4,\"index\":2,"
                + "\"year\":2025,\"duration\":2700000}}";

        PlexPayloadDto payload = new JsonMapper().readValue(json, PlexPayloadDto.class);

        assertEquals(new PlexMetadataDto("Cold Open", 2700000, "episode", "Slow Horses", 4, 2, 2025),
                payload.metadata());
        assertEquals(new PlexPlayerDto("Den TV"), payload.player());
    }

    @DisplayName("The three-argument metadata constructor leaves the extra fields empty")
    @Test
    public void metadataConvenienceConstructor() {
        PlexMetadataDto metadata = new PlexMetadataDto("Heat", 1, "movie");

        assertNull(metadata.grandparentTitle());
        assertNull(metadata.parentIndex());
        assertNull(metadata.index());
        assertNull(metadata.year());
    }

    @DisplayName("PlexPayloadDto has no metadata when the payload omits it")
    @Test
    public void payloadWithoutMetadata() throws Exception {
        PlexPayloadDto payload = new JsonMapper().readValue("{\"event\":\"media.stop\"}", PlexPayloadDto.class);

        assertNull(payload.metadata());
    }
}
