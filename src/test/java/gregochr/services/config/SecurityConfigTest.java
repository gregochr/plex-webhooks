package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Security Config Tests")
@SpringBootTest
@AutoConfigureMockMvc
public class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @DisplayName("GET /api/config is accessible without authentication")
    @Test
    public void testGetConfigNoAuth() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk());
    }

    @DisplayName("PUT /api/config/hue-enabled returns 401 without authentication")
    @Test
    public void testPutConfigNoAuth() throws Exception {
        mockMvc.perform(put("/api/config/hue-enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("PUT /api/config/hue-enabled returns 200 with valid credentials")
    @Test
    public void testPutConfigWithAuth() throws Exception {
        mockMvc.perform(put("/api/config/hue-enabled")
                        .with(httpBasic("admin", "cinelight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
    }

    @DisplayName("POST /plex is accessible without authentication")
    @Test
    public void testPlexWebhookNoAuth() throws Exception {
        String payload = "{\"event\":\"media.play\","
                + "\"Metadata\":{\"type\":\"movie\",\"title\":\"Test\",\"duration\":1000}}";
        mockMvc.perform(multipart("/plex")
                        .param("payload", payload))
                .andExpect(status().isOk());
    }

    @DisplayName("POST /api/debug/play returns 401 without authentication")
    @Test
    public void testDebugNoAuth() throws Exception {
        mockMvc.perform(post("/api/debug/play")
                        .param("type", "movie"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("POST /api/debug/play returns 200 with valid credentials")
    @Test
    public void testDebugWithAuth() throws Exception {
        mockMvc.perform(post("/api/debug/play")
                        .with(httpBasic("admin", "cinelight"))
                        .param("type", "movie"))
                .andExpect(status().isOk());
    }

    @DisplayName("PUT /api/config/hue-enabled returns 401 with wrong credentials")
    @Test
    public void testPutConfigWrongCredentials() throws Exception {
        mockMvc.perform(put("/api/config/hue-enabled")
                        .with(httpBasic("admin", "wrongpassword"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("GET /api/hue/status is accessible without authentication")
    @Test
    public void testGetHueStatusNoAuth() throws Exception {
        mockMvc.perform(get("/api/hue/status"))
                .andExpect(status().isOk());
    }
}
