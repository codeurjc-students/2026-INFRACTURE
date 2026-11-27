package es.codeurjc.infracture.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// A running process is not a healthy service: once its database goes away, /health says so and Docker marks
// the node unhealthy.
@SpringBootTest(properties = {
        "APP_ROLE=http", "APP_MODE=DATABASE_CRUD", "COMPONENT_NAME=orders-api", "RESPONSE_DELAY_MS=0" })
@AutoConfigureMockMvc
@Testcontainers
class DatabaseHealthTests {

    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18.7-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", () -> DATABASE.getJdbcUrl().substring("jdbc:".length()));
        registry.add("DATABASE_USER", DATABASE::getUsername);
        registry.add("DATABASE_PASSWORD", DATABASE::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void reportsDownOnceTheDatabaseStops() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());

        DATABASE.stop();

        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.status").value("DOWN"));
    }
}
