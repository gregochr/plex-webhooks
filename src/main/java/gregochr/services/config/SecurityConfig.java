package gregochr.services.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @SuppressFBWarnings(
            value = "SPRING_CSRF_PROTECTION_DISABLED",
            justification = "CineLight is a stateless, single-user home-network appliance using HTTP Basic auth. "
                    + "There are no browser session cookies, so CSRF is not a threat vector here."
    )
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**",
                        "/api/events", "/api/hue/**", "/api/dirigera/**",
                        "/api/config", "/api/config/sections").permitAll()
                .requestMatchers(HttpMethod.POST, "/plex").permitAll()
                .requestMatchers("/favicon.svg", "/philips-hue-logo.png", "/hue-logo.png").permitAll()
                // Health only: Docker's HEALTHCHECK and prod-health.sh call it without credentials. Everything
                // else under /actuator is denied outright, so an endpoint exposed later is not public by accident.
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").denyAll()
                .requestMatchers(HttpMethod.PUT, "/api/config/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/debug/**").authenticated()
                .anyRequest().permitAll()
            )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
            User.withUsername(adminUsername)
                .password("{noop}" + adminPassword)
                .roles("ADMIN")
                .build()
        );
    }
}
