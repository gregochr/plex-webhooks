package gregochr.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level check that the public config surfaces never carry credential values. Uses the full application
 * context (as SecurityConfigTest does) because the leak path spans SecurityConfig, PropertiesRepository and the
 * config views, none of which a sliced test would wire together.
 */
@DisplayName("Public Config Secrets Tests")
// Kept as @SpringBootTest: the property source, SecurityConfig and the config views must be wired together.
@SpringBootTest(properties = {
        "hue.application-key=SECRET-CANARY-1234",
        "app.admin.password=ADMIN-CANARY-5678",
        "hue.hub-address=10.20.30.40"
})
@AutoConfigureMockMvc
public class PublicConfigSecretsTest {

    @Autowired
    private MockMvc mockMvc;

    private String getBody(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString();
    }

    @DisplayName("Unauthenticated config sections do not contain the Hue application key")
    @Test
    public void testSectionsOmitHueApplicationKey() throws Exception {
        assertFalse(getBody("/api/config/sections").contains("SECRET-CANARY-1234"));
    }

    @DisplayName("Unauthenticated config sections do not contain the admin password")
    @Test
    public void testSectionsOmitAdminPassword() throws Exception {
        assertFalse(getBody("/api/config/sections").contains("ADMIN-CANARY-5678"));
    }

    @DisplayName("Unauthenticated config sections still carry non-secret values")
    @Test
    public void testSectionsStillCarryNonSecretValue() throws Exception {
        assertTrue(getBody("/api/config/sections").contains("10.20.30.40"));
    }

    @DisplayName("Unauthenticated config sections show a credential as set, without its value")
    @Test
    public void testSectionsShowCredentialAsSet() throws Exception {
        mockMvc.perform(get("/api/config/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[*].items[?(@.key == 'hue.application-key')].set").value(true))
                .andExpect(jsonPath("$.sections[*].items[?(@.key == 'hue.application-key')].value").isEmpty());
    }

    @DisplayName("Unauthenticated GET /api/config, the scene catalog and the playback views carry no credential")
    @Test
    public void testPublicApisOmitCredentials() throws Exception {
        for (String path : new String[] {"/api/config", "/api/hue/scenes", "/api/playback/now",
                "/api/playback/history"}) {
            String body = getBody(path);

            assertFalse(body.contains("SECRET-CANARY-1234"), path);
            assertFalse(body.contains("ADMIN-CANARY-5678"), path);
        }
    }

    @DisplayName("Unauthenticated GET /api/config returns the shared config shape from the real context")
    @Test
    public void testConfigShapeInFullContext() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hueIntegrationEnabled").value(true))
                .andExpect(jsonPath("$.scenes.start").value("04a673ca-d640-4bbb-bd94-7368434a0f94"))
                .andExpect(jsonPath("$.plexCommands.movie['plex.movie-play-enabled']").value(true))
                .andExpect(jsonPath("$.plexCommands.track['plex.track-play-enabled']").value(false));
    }

    @DisplayName("Unauthenticated Hue status does not contain the Hue application key")
    @Test
    public void testHueStatusOmitsApplicationKey() throws Exception {
        assertFalse(getBody("/api/hue/status").contains("SECRET-CANARY-1234"));
    }
}
