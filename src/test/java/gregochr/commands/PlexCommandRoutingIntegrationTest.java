package gregochr.commands;

import gregochr.controllers.hue.HueSceneController;
import gregochr.hue.client.HueBridgeClient;
import gregochr.services.config.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Real PlexCommand, PlexCommandConfig (built by its factory from properties), HueSceneController and ConfigService
 * together; only the Hue bridge is mocked. Covers every Plex event for every media type.
 */
@Tag("integration")
@DisplayName("Plex Command Routing Integration Tests")
public class PlexCommandRoutingIntegrationTest {

    private static final String START_SCENE = "04a673ca-d640-4bbb-bd94-7368434a0f94";
    private static final String STOP_SCENE = "f59f642e-89ed-45c2-9a53-2545dfe048b1";

    private final HueBridgeClient hueBridgeClient = mock(HueBridgeClient.class);
    private final ConfigService configService = new ConfigService();
    private final HueSceneController hueSceneController =
            new HueSceneController(hueBridgeClient, START_SCENE, STOP_SCENE);

    static Stream<Arguments> everyEventAndMediaType() {
        return Stream.of(PlexMediaType.values()).flatMap(mediaType -> Stream.of(PlexEvent.values())
                .map(event -> Arguments.of(event, mediaType, expectedScene(event))));
    }

    private static String expectedScene(PlexEvent event) {
        return switch (event) {
            case PLAY, RESUME -> START_SCENE;
            case STOP, PAUSE, SCROBBLE -> STOP_SCENE;
        };
    }

    private static PlexCommandConfig configEnabling(PlexEvent event, PlexMediaType mediaType) {
        MockEnvironment env = new MockEnvironment();
        if (event != null) {
            env.withProperty("plex." + mediaType.getType() + "-" + event.name().toLowerCase() + "-enabled", "true");
        }
        return new PlexCommandConfigFactory().plexCommandConfig(env);
    }

    @DisplayName("An enabled event activates its scene for its media type")
    @ParameterizedTest(name = "{0} {1} activates {2}")
    @MethodSource("everyEventAndMediaType")
    public void enabledEventActivatesScene(PlexEvent event, PlexMediaType mediaType, String scene) {
        when(hueBridgeClient.activateScene(scene)).thenReturn(true);
        PlexCommand command = new PlexCommand(event, "Good Will Hunting", 123, mediaType);

        boolean executed = command.execute(hueSceneController, configService, configEnabling(event, mediaType));

        assertTrue(executed);
        verify(hueBridgeClient).activateScene(scene);
    }

    @DisplayName("A disabled event leaves the lights alone for its media type")
    @ParameterizedTest(name = "{0} {1} disabled")
    @MethodSource("everyEventAndMediaType")
    public void disabledEventDoesNothing(PlexEvent event, PlexMediaType mediaType, String scene) {
        PlexCommand command = new PlexCommand(event, "Good Will Hunting", 123, mediaType);

        boolean executed = command.execute(hueSceneController, configService, configEnabling(null, mediaType));

        assertFalse(executed);
        verifyNoInteractions(hueBridgeClient);
    }

    @DisplayName("With Hue integration off, an enabled event leaves the lights alone")
    @ParameterizedTest(name = "{0} {1} with integration off")
    @MethodSource("everyEventAndMediaType")
    public void integrationOffDoesNothing(PlexEvent event, PlexMediaType mediaType, String scene) {
        configService.setHueIntegrationEnabled(false);
        PlexCommand command = new PlexCommand(event, "Good Will Hunting", 123, mediaType);

        boolean executed = command.execute(hueSceneController, configService, configEnabling(event, mediaType));

        assertFalse(executed);
        verifyNoInteractions(hueBridgeClient);
    }

    @DisplayName("An enabled event reports failure when the bridge rejects the scene")
    @ParameterizedTest(name = "{0} {1} bridge refuses {2}")
    @MethodSource("everyEventAndMediaType")
    public void bridgeFailureIsReported(PlexEvent event, PlexMediaType mediaType, String scene) {
        when(hueBridgeClient.activateScene(scene)).thenReturn(false);
        PlexCommand command = new PlexCommand(event, "Good Will Hunting", 123, mediaType);

        boolean executed = command.execute(hueSceneController, configService, configEnabling(event, mediaType));

        assertFalse(executed);
        verify(hueBridgeClient).activateScene(scene);
    }

    @DisplayName("Enabling a flag for one media type does not enable the same event for another")
    @ParameterizedTest(name = "{0} {1} does not leak to other media types")
    @MethodSource("everyEventAndMediaType")
    public void flagDoesNotLeakToOtherMediaTypes(PlexEvent event, PlexMediaType mediaType, String scene) {
        PlexCommandConfig config = configEnabling(event, mediaType);

        for (PlexMediaType other : PlexMediaType.values()) {
            if (other != mediaType) {
                PlexCommand command = new PlexCommand(event, "Good Will Hunting", 123, other);
                assertFalse(command.execute(hueSceneController, configService, config), other.getType());
            }
        }
        verifyNoInteractions(hueBridgeClient);
    }
}
