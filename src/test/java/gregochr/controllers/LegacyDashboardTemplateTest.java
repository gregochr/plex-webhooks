package gregochr.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A guard on the legacy Thymeleaf page's script (to be deleted with the page). History now replays stored titles,
 * so any server string spliced into markup would be a stored XSS. The page has no JS test harness, so this pins the
 * fix by its text.
 */
@DisplayName("Legacy Dashboard Template Tests")
public class LegacyDashboardTemplateTest {

    private static String template() throws IOException {
        try (InputStream in = LegacyDashboardTemplateTest.class.getResourceAsStream("/templates/index.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @DisplayName("The event list renders the event name and title with textContent, not innerHTML")
    @Test
    public void eventListUsesTextContent() throws Exception {
        String page = template();

        assertTrue(page.contains("title.textContent = e.command.title || 'Unknown Title';"));
        assertTrue(page.contains("e.command.event || 'unknown'"));
        assertFalse(page.contains("eventList.innerHTML"));
        assertFalse(page.contains("${e.command.title"));
        assertFalse(page.contains("${e.command.event"));
    }

    @DisplayName("Hue resource types from the bridge are not spliced into markup")
    @Test
    public void resourceTypesAreNotSplicedIntoMarkup() throws Exception {
        String page = template();

        assertFalse(page.contains("value=\"' + type + '\""));
        assertTrue(page.contains("labelEl.textContent = label;"));
    }
}
