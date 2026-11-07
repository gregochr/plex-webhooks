package gregochr.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The health endpoints Docker and prod-health.sh rely on, and the guarantee that nothing else under /actuator is
 * reachable. Full context, because the application.yml settings, actuator and SecurityConfig must work together.
 */
@Tag("integration")
@DisplayName("Actuator health and lock-down")
@SpringBootTest
@AutoConfigureMockMvc
public class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @DisplayName("anonymous GET /actuator/health/container is 200 with status UP and no component details")
    @Test
    public void testContainerGroupIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/container"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    @DisplayName("anonymous GET /actuator/health is 200 and shows only the status, never component details")
    @Test
    public void testAggregateHealthShowsNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @DisplayName("the container group is made of ping only, so a Hue, DIRIGERA or third-party outage cannot fail it")
    @Test
    public void testContainerGroupContainsOnlyPing() {
        assertEquals("ping", environment.getProperty("management.endpoint.health.group.container.include"));
    }

    @DisplayName("only the health endpoint is exposed over the web")
    @Test
    public void testOnlyHealthIsExposed() {
        assertEquals("health", environment.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("never", environment.getProperty("management.endpoint.health.show-details"));
    }

    @DisplayName("anonymous GET /actuator/env is not 200, and is rejected before it can leak the environment")
    @Test
    public void testEnvIsNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @DisplayName("GET /actuator/env is 403 even for the admin: denyAll, not just authenticated")
    @Test
    public void testEnvIsDeniedEvenForAdmin() throws Exception {
        mockMvc.perform(get("/actuator/env").with(httpBasic("admin", "cinelight")))
                .andExpect(status().isForbidden());
    }

    @DisplayName("an endpoint nobody exposed (/actuator/beans, /actuator/heapdump) is denied to everyone")
    @Test
    public void testOtherActuatorPathsAreDenied() throws Exception {
        mockMvc.perform(get("/actuator/beans").with(httpBasic("admin", "cinelight")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/heapdump").with(httpBasic("admin", "cinelight")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
    }

    @DisplayName("the public dashboard is still reachable without credentials")
    @Test
    public void testDashboardStillPublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }
}
