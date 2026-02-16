package gregochr.services.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("ConfigurationViewService Tests")
public class ConfigurationViewServiceTest {

    private ConfigurationViewService service;

    @BeforeEach
    public void setUp() {
        Environment environment = mock(Environment.class);
        service = new ConfigurationViewService(environment);
    }

    @DisplayName("Boolean type detected for 'true'")
    @Test
    public void testDetectBooleanTrue() {
        assertEquals("boolean", service.detectType("true"));
    }

    @DisplayName("Boolean type detected for 'false'")
    @Test
    public void testDetectBooleanFalse() {
        assertEquals("boolean", service.detectType("false"));
    }

    @DisplayName("Boolean type detected case-insensitively")
    @Test
    public void testDetectBooleanCaseInsensitive() {
        assertEquals("boolean", service.detectType("TRUE"));
        assertEquals("boolean", service.detectType("False"));
    }

    @DisplayName("Number type detected for integer")
    @Test
    public void testDetectNumberInteger() {
        assertEquals("number", service.detectType("10000"));
    }

    @DisplayName("Number type detected for decimal")
    @Test
    public void testDetectNumberDecimal() {
        assertEquals("number", service.detectType("3.14"));
    }

    @DisplayName("Text type for non-boolean non-numeric value")
    @Test
    public void testDetectText() {
        assertEquals("text", service.detectType("192.168.4.38"));
    }

    @DisplayName("Text type for empty string")
    @Test
    public void testDetectTextEmpty() {
        assertEquals("text", service.detectType(""));
    }

    @DisplayName("Duration detected for key containing 'interval'")
    @Test
    public void testDurationInterval() {
        assertTrue(service.isDuration("sse.heartbeat-interval"));
    }

    @DisplayName("Duration detected for key containing 'timeout'")
    @Test
    public void testDurationTimeout() {
        assertTrue(service.isDuration("http.connection-timeout"));
    }

    @DisplayName("Duration detected for key containing 'duration'")
    @Test
    public void testDurationDuration() {
        assertTrue(service.isDuration("animation.duration"));
    }

    @DisplayName("Duration not detected for regular key")
    @Test
    public void testNotDuration() {
        assertFalse(service.isDuration("hue.hub-address"));
    }

    @DisplayName("Label formatted: strips prefix, replaces hyphens, title-cases")
    @Test
    public void testFormatLabel() {
        assertEquals("Hub Address", service.formatLabel("hue.hub-address"));
    }

    @DisplayName("Label formatted for key without dot prefix")
    @Test
    public void testFormatLabelNoDot() {
        assertEquals("Debug", service.formatLabel("debug"));
    }

    @DisplayName("Label formatted for key with multiple hyphens")
    @Test
    public void testFormatLabelMultipleHyphens() {
        assertEquals("Admin Tab Enabled", service.formatLabel("dashboard.admin-tab-enabled"));
    }

    @DisplayName("Numeric display value formatted with commas")
    @Test
    public void testFormatDisplayValueNumber() {
        assertEquals("10,000", service.formatDisplayValue("10000", "number"));
    }

    @DisplayName("Text display value returned unchanged")
    @Test
    public void testFormatDisplayValueText() {
        assertEquals("192.168.4.38", service.formatDisplayValue("192.168.4.38", "text"));
    }

    @DisplayName("Boolean display value returned unchanged")
    @Test
    public void testFormatDisplayValueBoolean() {
        assertEquals("true", service.formatDisplayValue("true", "boolean"));
    }

    @DisplayName("buildItem creates correct item for boolean property")
    @Test
    public void testBuildItemBoolean() {
        ConfigurationViewItem item = service.buildItem("plex.movie-play-enabled", "true");

        assertEquals("plex.movie-play-enabled", item.key());
        assertEquals("Movie Play Enabled", item.label());
        assertEquals("true", item.value());
        assertEquals("true", item.displayValue());
        assertEquals("boolean", item.type());
        assertFalse(item.duration());
    }

    @DisplayName("buildItem creates correct item for numeric duration property")
    @Test
    public void testBuildItemNumericDuration() {
        ConfigurationViewItem item = service.buildItem("sse.heartbeat-interval", "10000");

        assertEquals("sse.heartbeat-interval", item.key());
        assertEquals("Heartbeat Interval", item.label());
        assertEquals("10000", item.value());
        assertEquals("10,000", item.displayValue());
        assertEquals("number", item.type());
        assertTrue(item.duration());
    }

    @DisplayName("buildItem creates correct item for text property")
    @Test
    public void testBuildItemText() {
        ConfigurationViewItem item = service.buildItem("hue.hub-address", "192.168.4.38");

        assertEquals("hue.hub-address", item.key());
        assertEquals("Hub Address", item.label());
        assertEquals("192.168.4.38", item.value());
        assertEquals("192.168.4.38", item.displayValue());
        assertEquals("text", item.type());
        assertFalse(item.duration());
    }

    @DisplayName("Sections with all boolean items are marked editable")
    @Test
    public void testAllBooleanSectionsEditable() {
        List<ConfigurationViewItem> boolItems = List.of(
                service.buildItem("plex.movie-play-enabled", "true"),
                service.buildItem("plex.movie-stop-enabled", "false")
        );
        assertTrue(service.isAllBoolean(boolItems));
    }

    @DisplayName("Sections with mixed types are not editable")
    @Test
    public void testMixedSectionsNotEditable() {
        List<ConfigurationViewItem> mixedItems = List.of(
                service.buildItem("hue.hub-address", "192.168.4.38"),
                service.buildItem("hue.status-interval", "10000")
        );
        assertFalse(service.isAllBoolean(mixedItems));
    }

    @DisplayName("Empty sections are not editable")
    @Test
    public void testEmptySectionNotEditable() {
        assertFalse(service.isAllBoolean(List.of()));
    }

    @DisplayName("saveProperties updates matching lines in file")
    @Test
    public void testSavePropertiesUpdatesLines(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of(
                "plex.movie-play-enabled=true",
                "plex.movie-stop-enabled=true"
        ));

        service.saveProperties(Map.of("plex.movie-play-enabled", "false"), file);

        List<String> result = Files.readAllLines(file);
        assertEquals("plex.movie-play-enabled=false", result.get(0));
        assertEquals("plex.movie-stop-enabled=true", result.get(1));
    }

    @DisplayName("saveProperties preserves comments and non-matching lines")
    @Test
    public void testSavePropertiesPreservesComments(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of(
                "# PLEX Movie Command Configuration",
                "plex.movie-play-enabled=true",
                "",
                "# Other section",
                "other.key=value"
        ));

        service.saveProperties(Map.of("plex.movie-play-enabled", "false"), file);

        List<String> result = Files.readAllLines(file);
        assertEquals("# PLEX Movie Command Configuration", result.get(0));
        assertEquals("plex.movie-play-enabled=false", result.get(1));
        assertEquals("", result.get(2));
        assertEquals("# Other section", result.get(3));
        assertEquals("other.key=value", result.get(4));
    }
}
