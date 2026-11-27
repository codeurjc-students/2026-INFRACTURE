package es.codeurjc.infracture.execution.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

// Plans stored before V20 were Compose files. The reference files are what PlanCompiler wrote for the same
// snapshot before and after the plan was typed, so a migrated row must read back as the plan it would get now.
@Testcontainers
class ExecutionPlanMigrationIntegrationTests {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void rewritesStoredComposePlansAsTypedPlansWithTheSameValues() throws IOException {
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("19").load().migrate();
        jdbc.update("""
                INSERT INTO users (id, username, encoded_password, email, display_name, role, created_at, updated_at)
                VALUES (1, 'alice', 'hash', 'alice@example.com', 'Alice', 'USER', now(), now())""");
        jdbc.update("""
                INSERT INTO projects (id, owner_id, name, created_at, updated_at)
                VALUES (1, 1, 'Lab', now(), now())""");
        jdbc.update("""
                INSERT INTO scenarios (id, project_id, name, revision, created_at, updated_at)
                VALUES (1, 1, 'Run', 1, now(), now())""");
        jdbc.update("""
                INSERT INTO executions (id, scenario_id, scenario_revision, phase, outcome, released_at, plan,
                    created_at, updated_at)
                VALUES (1, 1, 1, 'RELEASED', 'COMPLETED', now(), ?::jsonb, now(), now())""",
                reference("execution/compose-constant-http.json"));
        jdbc.update("""
                INSERT INTO executions (id, scenario_id, scenario_revision, phase, plan, created_at, updated_at)
                VALUES (2, 1, 1, 'RESERVED', ?::jsonb, now(), now())""", reference("execution/compose-stateless.json"));
        jdbc.update("""
                INSERT INTO executions (id, scenario_id, scenario_revision, phase, outcome, released_at, created_at,
                    updated_at)
                VALUES (3, 1, 1, 'RELEASED', 'FAILED', now(), now(), now())""");

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(plan(jdbc, 1))
                .isEqualTo(withoutCounters(json.readValue(reference("execution/constant-http-plan.json"),
                        ExecutionPlan.class)));
        assertThat(plan(jdbc, 2))
                .isEqualTo(withoutCounters(json.readValue(reference("execution/stateless-plan.json"),
                        ExecutionPlan.class)));
        assertThat(jdbc.queryForObject("SELECT plan FROM executions WHERE id = 3", String.class)).isNull();
    }

    // Stored plans migrated from Compose predate counters, so counters deserializes as null.
    private static ExecutionPlan withoutCounters(ExecutionPlan plan) {
        return new ExecutionPlan(plan.project(), plan.labels(),
                plan.nodes().stream()
                        .map(node -> new ExecutionPlan.Node(node.key(), node.host(), node.container(), node.wiring(),
                                node.health(), node.evidence(), null, node.limits()))
                        .toList(),
                plan.load());
    }

    private ExecutionPlan plan(JdbcTemplate jdbc, long id) {
        return json.readValue(jdbc.queryForObject("SELECT plan FROM executions WHERE id = ?", String.class, id),
                ExecutionPlan.class);
    }

    private static String reference(String path) throws IOException {
        try (InputStream input = ExecutionPlanMigrationIntegrationTests.class.getClassLoader()
                .getResourceAsStream(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
