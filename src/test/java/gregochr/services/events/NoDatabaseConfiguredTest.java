package gregochr.services.events;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With no {@code spring.datasource.url} (development, and every other test) nothing database-related exists. If the
 * exclude in application.yml named a class that is not on the classpath it would be ignored silently and Spring
 * Boot would try to build a DataSource, so this asserts the effect rather than the configuration.
 */
@DisplayName("No Database Configured Tests")
@SpringBootTest
@AutoConfigureMockMvc
public class NoDatabaseConfiguredTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @DisplayName("The context starts with no DataSource, JdbcClient, Flyway, writer or lifecycle bean")
    @Test
    public void noDatabaseBeans() {
        assertEquals(0, context.getBeansOfType(DataSource.class).size());
        assertEquals(0, context.getBeansOfType(JdbcClient.class).size());
        assertEquals(0, context.getBeansOfType(Flyway.class).size());
        assertEquals(0, context.getBeansOfType(PlaybackEventRepository.class).size());
        assertEquals(0, context.getBeansOfType(PlaybackHistoryWriter.class).size());
        assertEquals(0, context.getBeansOfType(PlaybackDatabaseLifecycle.class).size());
        assertEquals(0, context.getBeansOfType(DataSourceTransactionManager.class).size());
    }

    @DisplayName("The overall health endpoint is UP: with no DataSource there is no database health check to fail")
    @Test
    public void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
        mockMvc.perform(get("/actuator/health/container")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }
}
