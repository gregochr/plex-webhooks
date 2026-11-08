package gregochr.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /} is the React single-page app (a static {@code index.html}, served by Spring Boot's welcome page);
 * the legacy {@code /classic} page no longer exists. The tests use the fixture
 * {@code src/test/resources/static/index.html}, so they pass without a frontend build.
 */
@DisplayName("SPA Serving Tests")
// Kept as @SpringBootTest: the welcome page and SecurityConfig must be wired together.
@SpringBootTest
@AutoConfigureMockMvc
public class SpaServingTest {

    private static final String LEGACY_MARKER = "new EventSource(";

    @Autowired
    private MockMvc mockMvc;

    @DisplayName("GET / is routed to the static SPA index.html, which is served without authentication")
    @Test
    public void testRootServesSpa() throws Exception {
        // MockMvc does not follow the welcome-page forward, so check both hops.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        String body = mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("spa-fixture-root"), body);
        assertFalse(body.contains(LEGACY_MARKER), "the legacy page must not be the SPA");
    }

    @DisplayName("GET /classic is gone: the legacy Thymeleaf dashboard was retired")
    @Test
    public void testClassicIsNotFound() throws Exception {
        mockMvc.perform(get("/classic")).andExpect(status().isNotFound());
    }
}
