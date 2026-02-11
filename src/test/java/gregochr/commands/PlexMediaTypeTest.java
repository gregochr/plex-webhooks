package gregochr.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Plex Media Type Tests")
public class PlexMediaTypeTest {

    @DisplayName("fromType returns MOVIE for 'movie'")
    @Test
    public void testFromTypeMovie() {
        Optional<PlexMediaType> result = PlexMediaType.fromType("movie");

        assertTrue(result.isPresent());
        assertEquals(PlexMediaType.MOVIE, result.get());
    }

    @DisplayName("fromType returns EPISODE for 'episode'")
    @Test
    public void testFromTypeEpisode() {
        Optional<PlexMediaType> result = PlexMediaType.fromType("episode");

        assertTrue(result.isPresent());
        assertEquals(PlexMediaType.EPISODE, result.get());
    }

    @DisplayName("fromType returns CLIP for 'clip'")
    @Test
    public void testFromTypeClip() {
        Optional<PlexMediaType> result = PlexMediaType.fromType("clip");

        assertTrue(result.isPresent());
        assertEquals(PlexMediaType.CLIP, result.get());
    }

    @DisplayName("fromType returns TRACK for 'track'")
    @Test
    public void testFromTypeTrack() {
        Optional<PlexMediaType> result = PlexMediaType.fromType("track");

        assertTrue(result.isPresent());
        assertEquals(PlexMediaType.TRACK, result.get());
    }

    @DisplayName("fromType returns PHOTO for 'photo'")
    @Test
    public void testFromTypePhoto() {
        Optional<PlexMediaType> result = PlexMediaType.fromType("photo");

        assertTrue(result.isPresent());
        assertEquals(PlexMediaType.PHOTO, result.get());
    }

    @DisplayName("fromType returns empty for unknown type")
    @Test
    public void testFromTypeUnknown() {
        Optional<PlexMediaType> result = PlexMediaType.fromType("unknown");

        assertTrue(result.isEmpty());
    }

    @DisplayName("getType returns correct string for MOVIE")
    @Test
    public void testGetTypeMovie() {
        assertEquals("movie", PlexMediaType.MOVIE.getType());
    }

    @DisplayName("getType returns correct string for EPISODE")
    @Test
    public void testGetTypeEpisode() {
        assertEquals("episode", PlexMediaType.EPISODE.getType());
    }

    @DisplayName("getType returns correct string for CLIP")
    @Test
    public void testGetTypeClip() {
        assertEquals("clip", PlexMediaType.CLIP.getType());
    }

    @DisplayName("getType returns correct string for TRACK")
    @Test
    public void testGetTypeTrack() {
        assertEquals("track", PlexMediaType.TRACK.getType());
    }

    @DisplayName("getType returns correct string for PHOTO")
    @Test
    public void testGetTypePhoto() {
        assertEquals("photo", PlexMediaType.PHOTO.getType());
    }
}
