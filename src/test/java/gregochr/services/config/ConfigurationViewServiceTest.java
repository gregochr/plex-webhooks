package gregochr.services.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        assertTrue(keys.contains("dirigera.status-interval"));
        assertEquals(3, keys.size());
    }

    @DisplayName("saveProperties publishes PropertiesUpdatedEvent after save")
    @Test
    public void testSavePropertiesPublishesEvent() throws IOException {
        Map<String, String> updates = Map.of("sse.heartbeat-interval", "60000");
        when(repository.resolvePropertiesPath()).thenReturn(java.nio.file.Path.of("test.properties"));

        service.saveProperties(updates);

        ArgumentCaptor<PropertiesUpdatedEvent> captor = ArgumentCaptor.forClass(PropertiesUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(updates, captor.getValue().getUpdatedProperties());
    }
}
