package gregochr.services.config;

import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexMediaType;
import gregochr.commands.PlexMediaTypeConfig;
import gregochr.controllers.hue.HueSceneController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Client Config Service Tests")
public class ClientConfigServiceTest {

    private ConfigService configService;
    private HueSceneController hueSceneController;
    private PlexCommandConfig plexCommandConfig;
    private ClientConfigService service;

    @BeforeEach
    public void setUp() {
        configService = new ConfigService();
        hueSceneController = mock(HueSceneController.class);
        when(hueSceneController.getStartScene()).thenReturn("start-id");
        when(hueSceneController.getStopScene()).thenReturn("stop-id");
        Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(PlexMediaType.class);
        configs.put(PlexMediaType.MOVIE, new PlexMediaTypeConfig(true, true, false, false, true));
        configs.put(PlexMediaType.EPISODE, new PlexMediaTypeConfig(false, false, true, true, false));
        plexCommandConfig = new PlexCommandConfig(configs);
        service = new ClientConfigService(configService, hueSceneController, plexCommandConfig);
    }

    @DisplayName("The snapshot serialises to exactly the documented shape, with all five media types in order")
    @Test
    public void snapshotShape() {
        JsonMapper mapper = JsonMapper.builder().disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build();

        assertEquals("{\"hueIntegrationEnabled\":true,\"scenes\":{\"start\":\"start-id\",\"stop\":\"stop-id\"},"
                + "\"plexCommands\":{"
                + "\"movie\":{\"plex.movie-play-enabled\":true,\"plex.movie-resume-enabled\":true,"
                + "\"plex.movie-stop-enabled\":false,\"plex.movie-pause-enabled\":false,"
                + "\"plex.movie-scrobble-enabled\":true},"
                + "\"episode\":{\"plex.episode-play-enabled\":false,\"plex.episode-resume-enabled\":false,"
                + "\"plex.episode-stop-enabled\":true,\"plex.episode-pause-enabled\":true,"
                + "\"plex.episode-scrobble-enabled\":false},"
                + "\"clip\":{\"plex.clip-play-enabled\":false,\"plex.clip-resume-enabled\":false,"
                + "\"plex.clip-stop-enabled\":false,\"plex.clip-pause-enabled\":false,"
                + "\"plex.clip-scrobble-enabled\":false},"
                + "\"track\":{\"plex.track-play-enabled\":false,\"plex.track-resume-enabled\":false,"
                + "\"plex.track-stop-enabled\":false,\"plex.track-pause-enabled\":false,"
                + "\"plex.track-scrobble-enabled\":false},"
                + "\"photo\":{\"plex.photo-play-enabled\":false,\"plex.photo-resume-enabled\":false,"
                + "\"plex.photo-stop-enabled\":false,\"plex.photo-pause-enabled\":false,"
                + "\"plex.photo-scrobble-enabled\":false}}}",
                mapper.writeValueAsString(service.snapshot()));
    }

    @DisplayName("The snapshot follows the Hue integration switch")
    @Test
    public void followsHueSwitch() {
        configService.setHueIntegrationEnabled(false);

        assertFalse(service.snapshot().hueIntegrationEnabled());
    }

    @DisplayName("The snapshot reads the live state: a hot-reloaded media type and scene show immediately")
    @Test
    public void readsLiveState() {
        plexCommandConfig.updateMediaType(PlexMediaType.CLIP, new PlexMediaTypeConfig(true, true, true, true, true));
        when(hueSceneController.getStartScene()).thenReturn("new-start");

        ClientConfig snapshot = service.snapshot();

        assertEquals(true, snapshot.plexCommands().get("clip").get("plex.clip-scrobble-enabled"));
        assertEquals("new-start", snapshot.scenes().start());
    }
}
