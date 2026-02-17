package gregochr.services.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ConfigurationViewFormatter Tests")
public class ConfigurationViewFormatterTest {

    private ConfigurationViewFormatter formatter;

    @BeforeEach
    public void setUp() {
        formatter = new ConfigurationViewFormatter();
    }

    @DisplayName("Boolean type detected for 'true'")
    @Test
    public void testDetectBooleanTrue() {
        assertEquals("boolean", formatter.detectType("true"));
    }

    @DisplayName("Boolean type detected for 'false'")
    @Test
    public void testDetectBooleanFalse() {
        assertEquals("boolean", formatter.detectType("false"));
    }

    @DisplayName("Boolean type detected case-insensitively")
    @Test
    public void testDetectBooleanCaseInsensitive() {
        assertEquals("boolean", formatter.detectType("TRUE"));
        assertEquals("boolean", formatter.detectType("False"));
    }

    @DisplayName("Number type detected for integer")
    @Test
    public void testDetectNumberInteger() {
        assertEquals("number", formatter.detectType("10000"));
    }

    @DisplayName("Number type detected for decimal")
    @Test
    public void testDetectNumberDecimal() {
        assertEquals("number", formatter.detectType("3.14"));
    }

    @DisplayName("Text type for non-boolean non-numeric value")
    @Test
    public void testDetectText() {
        assertEquals("text", formatter.detectType("192.168.4.38"));
    }

    @DisplayName("Text type for empty string")
    @Test
    public void testDetectTextEmpty() {
        assertEquals("text", formatter.detectType(""));
    }

    @DisplayName("Duration detected for key containing 'interval'")
    @Test
    public void testDurationInterval() {
        assertTrue(formatter.isDuration("sse.heartbeat-interval"));
    }

    @DisplayName("Duration detected for key containing 'timeout'")
    @Test
    public void testDurationTimeout() {
        assertTrue(formatter.isDuration("http.connection-timeout"));
    }

    @DisplayName("Duration detected for key containing 'duration'")
    @Test
    public void testDurationDuration() {
        assertTrue(formatter.isDuration("animation.duration"));
    }

    @DisplayName("Duration not detected for regular key")
    @Test
    public void testNotDuration() {
        assertFalse(formatter.isDuration("hue.hub-address"));
    }

    @DisplayName("Label formatted: strips prefix, replaces hyphens, title-cases")
    @Test
    public void testFormatLabel() {
        assertEquals("Hub Address", formatter.formatLabel("hue.hub-address"));
    }

    @DisplayName("Label formatted for key without dot prefix")
    @Test
    public void testFormatLabelNoDot() {
        assertEquals("Debug", formatter.formatLabel("debug"));
    }

    @DisplayName("Label formatted for key with multiple hyphens")
    @Test
    public void testFormatLabelMultipleHyphens() {
        assertEquals("Admin Tab Enabled", formatter.formatLabel("dashboard.admin-tab-enabled"));
    }

    @DisplayName("Numeric display value formatted with commas")
    @Test
    public void testFormatDisplayValueNumber() {
        assertEquals("10,000", formatter.formatDisplayValue("10000", "number"));
    }

    @DisplayName("Text display value returned unchanged")
    @Test
    public void testFormatDisplayValueText() {
        assertEquals("192.168.4.38", formatter.formatDisplayValue("192.168.4.38", "text"));
    }

    @DisplayName("Boolean display value returned unchanged")
    @Test
    public void testFormatDisplayValueBoolean() {
        assertEquals("true", formatter.formatDisplayValue("true", "boolean"));
    }

    @DisplayName("buildItem creates correct item for boolean property")
    @Test
    public void testBuildItemBoolean() {
        ConfigurationViewItem item = formatter.buildItem("plex.movie-play-enabled", "true");

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
        ConfigurationViewItem item = formatter.buildItem("sse.heartbeat-interval", "10000");

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
        ConfigurationViewItem item = formatter.buildItem("hue.hub-address", "192.168.4.38");

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
                formatter.buildItem("plex.movie-play-enabled", "true"),
                formatter.buildItem("plex.movie-stop-enabled", "false")
        );
        assertTrue(formatter.isEditable(boolItems));
    }

    @DisplayName("Sections with all number items are marked editable")
    @Test
    public void testAllNumberSectionsEditable() {
        List<ConfigurationViewItem> numberItems = List.of(
                formatter.buildItem("sse.heartbeat-interval", "10000")
        );
        assertTrue(formatter.isEditable(numberItems));
    }

    @DisplayName("Sections with mixed types including text are not editable")
    @Test
    public void testMixedSectionsNotEditable() {
        List<ConfigurationViewItem> mixedItems = List.of(
                formatter.buildItem("hue.hub-address", "192.168.4.38"),
                formatter.buildItem("hue.status-interval", "10000")
        );
        assertFalse(formatter.isEditable(mixedItems));
    }

    @DisplayName("Empty sections are not editable")
    @Test
    public void testEmptySectionNotEditable() {
        assertFalse(formatter.isEditable(List.of()));
    }
}
