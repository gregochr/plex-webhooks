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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level check that the public dashboard never carries credential values. Uses the full application
 * context (as SecurityConfigTest does) because the leak path spans SecurityConfig, PropertiesRepository,
 * the formatter and the Thymeleaf template, none of which a sliced test would wire together.
 */
@DisplayName("Dashboard Secrets Tests")
// Kept as @SpringBootTest: the property source, SecurityConfig, formatter and template must be wired together.
@SpringBootTest(properties = {
        "hue.application-key=SECRET-CANARY-1234",
        "app.admin.password=ADMIN-CANARY-5678",
        "hue.hub-address=10.20.30.40"
})
@AutoConfigureMockMvc
public class DashboardSecretsTest {

    @Autowired
    private MockMvc mockMvc;

    private String getDashboardBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString();
    }

    @DisplayName("Unauthenticated dashboard does not contain the Hue application key")
    @Test
    public void testDashboardOmitsHueApplicationKey() throws Exception {
        assertFalse(getDashboardBody().contains("SECRET-CANARY-1234"));
    }

    @DisplayName("Unauthenticated dashboard does not contain the admin password")
    @Test
    public void testDashboardOmitsAdminPassword() throws Exception {
        assertFalse(getDashboardBody().contains("ADMIN-CANARY-5678"));
    }

    @DisplayName("Unauthenticated dashboard still renders non-secret config values")
    @Test
    public void testDashboardStillRendersNonSecretValue() throws Exception {
        assertTrue(getDashboardBody().contains("10.20.30.40"));
    }

    @DisplayName("Unauthenticated dashboard shows credentials as set without their values")
    @Test
    public void testDashboardShowsCredentialAsSet() throws Exception {
        assertTrue(getDashboardBody().contains("•••• (set)"));
    }

    @DisplayName("Unauthenticated Hue status does not contain the Hue application key")
    @Test
    public void testHueStatusOmitsApplicationKey() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/hue/status")).andExpect(status().isOk()).andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("SECRET-CANARY-1234"));
    }
}
