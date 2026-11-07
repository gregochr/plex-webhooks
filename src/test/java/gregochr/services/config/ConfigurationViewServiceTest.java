package gregochr.services.config;

import gregochr.commands.PlexMediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ConfigurationViewService Tests")
public class ConfigurationViewServiceTest {

    private PropertiesRepository repository;
    private ConfigurationViewFormatter formatter;
    private ApplicationEventPublisher eventPublisher;
    private ConfigurationViewService service;

    @BeforeEach
    public void setUp() {
        repository = mock(PropertiesRepository.class);
        formatter = mock(ConfigurationViewFormatter.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ConfigurationViewService(repository, formatter, eventPublisher);
    }

    @DisplayName("getConfigurationSections delegates to repository and formatter")
    @Test
    public void testGetConfigurationSectionsDelegates() {
        List<PropertySection> raw = List.of(
                new PropertySection("Test", List.of(new AbstractMap.SimpleEntry<>("key", "value")))
        );
        List<ConfigurationViewSection> expected = List.of(
                new ConfigurationViewSection("Test", List.of(), false)
        );
        when(repository.readAllEntries()).thenReturn(raw);
        when(formatter.buildSections(raw)).thenReturn(expected);

        List<ConfigurationViewSection> result = service.getConfigurationSections();

        assertEquals(expected, result);
        verify(repository).readAllEntries();
        verify(formatter).buildSections(raw);
    }

    @DisplayName("getPlexProperties delegates to repository with correct prefix")
    @Test
    public void testGetPlexPropertiesDelegates() {
        Map<String, Boolean> expected = Map.of("plex.movie-play-enabled", true);
        when(repository.readBooleanProperties("plex.movie-")).thenReturn(expected);

        Map<String, Boolean> result = service.getPlexProperties("movie");

        assertEquals(expected, result);
        verify(repository).readBooleanProperties("plex.movie-");
    }

    @DisplayName("getSceneProperties delegates to repository")
    @Test
    public void testGetScenePropertiesDelegates() {
        Map<String, String> expected = Map.of("hue.start-scene", "abc");
        when(repository.readSceneProperties()).thenReturn(expected);

        Map<String, String> result = service.getSceneProperties();

        assertEquals(expected, result);
        verify(repository).readSceneProperties();
    }

    @DisplayName("isValidMediaType accepts movie, episode, clip")
    @Test
    public void testValidMediaTypes() {
        assertTrue(service.isValidMediaType("movie"));
        assertTrue(service.isValidMediaType("episode"));
        assertTrue(service.isValidMediaType("clip"));
        assertFalse(service.isValidMediaType("unknown"));
    }

    @DisplayName("isValidSceneKey accepts hue.start-scene and hue.stop-scene")
    @Test
    public void testValidSceneKeys() {
        assertTrue(service.isValidSceneKey("hue.start-scene"));
        assertTrue(service.isValidSceneKey("hue.stop-scene"));
        assertFalse(service.isValidSceneKey("hue.invalid-key"));
    }

    @DisplayName("getValidPropertyKeys returns expected keys")
    @Test
    public void testValidPropertyKeys() {
        Set<String> keys = service.getValidPropertyKeys();
        assertTrue(keys.contains("sse.heartbeat-interval"));
        assertTrue(keys.contains("hue.status-interval"));
        assertTrue(keys.contains("hue.scene-catalog-interval"));
        assertTrue(keys.contains("dirigera.status-interval"));
        assertEquals(4, keys.size());
    }

    @DisplayName("saveProperties publishes PropertiesUpdatedEvent after save")
    @Test
    public void testSavePropertiesPublishesEvent() throws IOException {
        Map<String, String> updates = Map.of("sse.heartbeat-interval", "60000");
        when(repository.resolvePropertiesPath()).thenReturn(Path.of("test.properties"));

        service.saveProperties(updates);

        ArgumentCaptor<PropertiesUpdatedEvent> captor = ArgumentCaptor.forClass(PropertiesUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(updates, captor.getValue().getUpdatedProperties());
    }

    @DisplayName("saveProperties writes the updates to the resolved file before publishing the event")
    @Test
    public void testSavePropertiesWritesBeforePublishing() throws IOException {
        Map<String, String> updates = Map.of("sse.heartbeat-interval", "60000");
        Path file = Path.of("test.properties");
        when(repository.resolvePropertiesPath()).thenReturn(file);

        service.saveProperties(updates);

        InOrder order = inOrder(repository, eventPublisher);
        order.verify(repository).saveProperties(updates, file);
        order.verify(eventPublisher).publishEvent(ArgumentCaptor.forClass(PropertiesUpdatedEvent.class).capture());
    }

    @DisplayName("saveProperties publishes no event when the file cannot be written")
    @Test
    public void testSavePropertiesFailureDoesNotPublish() throws IOException {
        Path file = Path.of("test.properties");
        Map<String, String> updates = Map.of("sse.heartbeat-interval", "60000");
        when(repository.resolvePropertiesPath()).thenReturn(file);
        doThrow(new IOException("disk full")).when(repository).saveProperties(updates, file);

        assertThrows(IOException.class, () -> service.saveProperties(updates));

        verifyNoInteractions(eventPublisher);
    }

    @DisplayName("isValidMediaType accepts every PlexMediaType, including track and photo")
    @Test
    public void testEveryEnumMediaTypeIsValid() {
        for (PlexMediaType mediaType : PlexMediaType.values()) {
            assertTrue(service.isValidMediaType(mediaType.getType()), mediaType.getType());
        }
        assertTrue(service.isValidMediaType("track"));
        assertTrue(service.isValidMediaType("photo"));
    }

    @DisplayName("isValidPropertyValue accepts an interval from 1000 to 86400000 ms inclusive")
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"1000", "1001", "30000", "86399999", "86400000"})
    public void testIntervalBoundsAccepted(String value) {
        for (String key : service.getValidPropertyKeys()) {
            assertTrue(service.isValidPropertyValue(key, value), key);
        }
    }

    @DisplayName("isValidPropertyValue rejects non-numeric, empty, out-of-range and non-whole intervals")
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"abc", "", " ", "0", "-1", "999", "86400001", "1000.5", "1e3", " 5000", "5000 ",
            "+5000", "99999999999999999999", "9999999999999999999"})
    public void testIntervalRejected(String value) {
        for (String key : service.getValidPropertyKeys()) {
            assertFalse(service.isValidPropertyValue(key, value), key);
        }
    }

    @DisplayName("isValidPropertyValue rejects a null interval")
    @Test
    public void testNullIntervalRejected() {
        assertFalse(service.isValidPropertyValue("sse.heartbeat-interval", null));
    }

    @DisplayName("isValidPropertyValue puts no constraint on keys that are not intervals")
    @Test
    public void testNonIntervalKeyUnconstrained() {
        assertTrue(service.isValidPropertyValue("hue.start-scene", "abc"));
    }

    @DisplayName("parseIntervalMs returns the value in range and null outside it")
    @Test
    public void testParseIntervalMs() {
        assertEquals(1000L, ConfigurationViewService.parseIntervalMs("1000"));
        assertEquals(86_400_000L, ConfigurationViewService.parseIntervalMs("86400000"));
        assertNull(ConfigurationViewService.parseIntervalMs("999"));
        assertNull(ConfigurationViewService.parseIntervalMs("86400001"));
        assertNull(ConfigurationViewService.parseIntervalMs(null));
    }

    @DisplayName("isValidMediaType is case sensitive and does not trim")
    @Test
    public void testMediaTypeMatchIsExact() {
        assertFalse(service.isValidMediaType("Movie"));
        assertFalse(service.isValidMediaType("movie "));
        assertFalse(service.isValidMediaType(""));
    }

    @DisplayName("isValidSceneKey is case sensitive and rejects a bare suffix")
    @Test
    public void testSceneKeyMatchIsExact() {
        assertFalse(service.isValidSceneKey("HUE.START-SCENE"));
        assertFalse(service.isValidSceneKey("start-scene"));
        assertFalse(service.isValidSceneKey(""));
    }

    @DisplayName("getValidPropertyKeys does not allow the credential keys")
    @Test
    public void testPropertyKeysExcludeCredentials() {
        Set<String> keys = service.getValidPropertyKeys();

        assertFalse(keys.contains("hue.application-key"));
        assertFalse(keys.contains("app.admin.password"));
        assertFalse(keys.contains("hue.hub-address"));
    }
}
