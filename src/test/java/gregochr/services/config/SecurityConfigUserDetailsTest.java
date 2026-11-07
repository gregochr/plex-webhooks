package gregochr.services.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Security Config User Details Tests")
public class SecurityConfigUserDetailsTest {

    private UserDetailsService userDetailsService;

    @BeforeEach
    public void setUp() {
        SecurityConfig config = new SecurityConfig();
        // The admin credentials are field-injected with @Value, which a plain unit test does not process
        ReflectionTestUtils.setField(config, "adminUsername", "admin");
        ReflectionTestUtils.setField(config, "adminPassword", "cinelight");
        userDetailsService = config.userDetailsService();
    }

    @DisplayName("The configured admin user exists with a noop-encoded password")
    @Test
    public void adminUserHasConfiguredPassword() {
        UserDetails admin = userDetailsService.loadUserByUsername("admin");

        assertEquals("admin", admin.getUsername());
        assertEquals("{noop}cinelight", admin.getPassword());
    }

    @DisplayName("The admin user has exactly the ADMIN role")
    @Test
    public void adminUserHasAdminRole() {
        UserDetails admin = userDetailsService.loadUserByUsername("admin");

        assertEquals(List.of("ROLE_ADMIN"), admin.getAuthorities().stream().map(a -> a.getAuthority()).toList());
    }

    @DisplayName("Any other username is unknown")
    @Test
    public void otherUsernameIsUnknown() {
        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername("root"));
    }
}
