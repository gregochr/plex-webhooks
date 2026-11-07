package gregochr.services.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
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

    @DisplayName("buildItem blanks the value of hue.application-key and marks it secret and set")
    @Test
    public void testBuildItemHueApplicationKeySet() {
        ConfigurationViewItem item = formatter.buildItem("hue.application-key", "SECRET-CANARY-1234");

        assertEquals("hue.application-key", item.key());
        assertEquals("Application Key", item.label());
        assertEquals("", item.value());
        assertEquals("Set", item.displayValue());
        assertEquals("secret", item.type());
        assertTrue(item.secret());
        assertFalse(item.duration());
    }

    @DisplayName("buildItem blanks the value of app.admin.password and marks it secret and set")
    @Test
    public void testBuildItemAdminPasswordSet() {
        ConfigurationViewItem item = formatter.buildItem("app.admin.password", "hunter2");

        assertEquals("", item.value());
        assertEquals("Set", item.displayValue());
        assertTrue(item.secret());
    }

    @DisplayName("buildItem marks an empty credential as not set")
    @Test
    public void testBuildItemCredentialEmpty() {
        ConfigurationViewItem item = formatter.buildItem("hue.application-key", "");

        assertEquals("", item.value());
        assertEquals("Not set", item.displayValue());
        assertTrue(item.secret());
    }

    @DisplayName("buildItem marks a whitespace-only credential as not set")
    @Test
    public void testBuildItemCredentialBlank() {
        ConfigurationViewItem item = formatter.buildItem("app.admin.password", "   ");

        assertEquals("Not set", item.displayValue());
    }

    @DisplayName("buildItem marks a null credential as not set")
    @Test
    public void testBuildItemCredentialNull() {
        ConfigurationViewItem item = formatter.buildItem("app.admin.password", null);

        assertEquals("", item.value());
        assertEquals("Not set", item.displayValue());
    }

    @DisplayName("buildItem blanks token, secret and api-key credentials")
    @Test
    public void testBuildItemOtherCredentialShapes() {
        assertEquals("", formatter.buildItem("x.access-token", "abc").value());
        assertEquals("", formatter.buildItem("x.client-secret", "abc").value());
        assertEquals("", formatter.buildItem("x.api-key", "abc").value());
    }

    @DisplayName("buildItem leaves hue.hub-id visible")
    @Test
    public void testBuildItemHubIdVisible() {
        ConfigurationViewItem item = formatter.buildItem("hue.hub-id", "001788fffe7107c8");

        assertEquals("001788fffe7107c8", item.value());
        assertEquals("001788fffe7107c8", item.displayValue());
        assertFalse(item.secret());
    }

    @DisplayName("buildItem leaves a key ending in 'monkey' unchanged")
    @Test
    public void testBuildItemMonkeyNotSecret() {
        ConfigurationViewItem item = formatter.buildItem("zoo.monkey", "george");

        assertEquals("george", item.value());
        assertFalse(item.secret());
    }

    @DisplayName("buildItem leaves a key containing 'keyboard' unchanged")
    @Test
    public void testBuildItemKeyboardNotSecret() {
        ConfigurationViewItem item = formatter.buildItem("hue.hub-keyboard", "qwerty");

        assertEquals("qwerty", item.value());
        assertFalse(item.secret());
    }

    @DisplayName("buildSections does not make a section with a credential editable")
    @Test
    public void testBuildSectionsCredentialSectionNotEditable() {
        List<ConfigurationViewSection> sections = formatter.buildSections(List.of(
                new PropertySection("Hue", List.of(
                        new AbstractMap.SimpleEntry<>("hue.application-key", "SECRET-CANARY-1234"),
                        new AbstractMap.SimpleEntry<>("hue.status-interval", "10000")))));

        assertFalse(sections.get(0).editable());
        assertEquals("", sections.get(0).items().get(0).value());
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

    @DisplayName("buildSections hides scene keys, drops empty sections and flags editable ones")
    @Test
    public void testBuildSections() {
        List<PropertySection> raw = List.of(
                new PropertySection("Plex", List.of(
                        new AbstractMap.SimpleEntry<>("plex.movie-play-enabled", "true"),
                        new AbstractMap.SimpleEntry<>("hue.status-interval", "10000"))),
                new PropertySection("Hue", List.of(
                        new AbstractMap.SimpleEntry<>("hue.hub-address", "192.168.4.38"),
                        new AbstractMap.SimpleEntry<>("hue.start-scene", "abc"))),
                new PropertySection("Scenes", List.of(
                        new AbstractMap.SimpleEntry<>("hue.start-scene", "abc"),
                        new AbstractMap.SimpleEntry<>("hue.stop-scene", "def"))));

        List<ConfigurationViewSection> sections = formatter.buildSections(raw);

        assertEquals(2, sections.size());
        assertEquals("Plex", sections.get(0).title());
        assertTrue(sections.get(0).editable());
        assertEquals(2, sections.get(0).items().size());
        assertEquals("Movie Play Enabled", sections.get(0).items().get(0).label());
        assertEquals("Hue", sections.get(1).title());
        assertFalse(sections.get(1).editable());
        assertEquals(1, sections.get(1).items().size());
        assertEquals("hue.hub-address", sections.get(1).items().get(0).key());
    }

    @DisplayName("buildSections returns an empty list for no input")
    @Test
    public void testBuildSectionsEmpty() {
        assertTrue(formatter.buildSections(List.of()).isEmpty());
    }

    @DisplayName("formatDisplayValue formats integers with grouping separators")
    @Test
    public void testFormatDisplayValueIntegerGrouping() {
        assertEquals(java.text.NumberFormat.getNumberInstance().format(1234567L),
                formatter.formatDisplayValue("1234567", "number"));
    }

    @DisplayName("formatDisplayValue formats decimals via the double path")
    @Test
    public void testFormatDisplayValueDecimal() {
        assertEquals(java.text.NumberFormat.getNumberInstance().format(1234.5),
                formatter.formatDisplayValue("1234.5", "number"));
    }

    @DisplayName("formatDisplayValue returns the raw value when a number-typed value cannot be parsed")
    @Test
    public void testFormatDisplayValueUnparseableNumber() {
        assertEquals("abc", formatter.formatDisplayValue("abc", "number"));
    }
}
