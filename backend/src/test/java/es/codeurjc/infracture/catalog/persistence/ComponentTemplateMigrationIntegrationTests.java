package es.codeurjc.infracture.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ComponentTemplateMigrationIntegrationTests {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Test
    void upgradesExistingCataloguePreservingDataAndUniqueKeys() {
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("2").load().migrate();
        jdbc.update("UPDATE component_template SET name = 'Existing worker', enabled = false WHERE template_key = 'worker'");
        String original = "SELECT id, template_key, name, type, enabled FROM component_template ORDER BY id";
        var existingTemplates = jdbc.queryForList(original);
        assertThat(existingTemplates).hasSize(6);

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbc.queryForList(original)).isEqualTo(existingTemplates.stream()
                .filter(template -> !"LOAD_GENERATOR".equals(template.get("type"))).toList());
        assertThat(jdbc.queryForObject(
                "SELECT initial_configuration ->> 'profile' FROM component_template WHERE template_key = 'worker'",
                String.class)).isEqualTo("CONSUMER");
        assertThat(jdbc.update("""
                INSERT INTO component_template (template_key, name, type, enabled, initial_configuration)
                VALUES ('worker-variant', 'Worker variant', 'WORKER', true, '{"profile": "CONSUMER", "parameters": {}}')
                """)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO component_template (template_key, name, type, enabled, initial_configuration)
                VALUES ('worker-variant', 'Duplicate key', 'REDIS', true, '{"profile": "DEFAULT", "parameters": {}}')
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_component_template_key");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO component_template (template_key, name, type, enabled)
                VALUES ('no-initial', 'No initial configuration', 'REDIS', true)
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("initial_configuration");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO component_template (template_key, name, type, enabled, initial_configuration)
                VALUES ('load-again', 'Load Generator', 'LOAD_GENERATOR', true, '{"profile": "DEFAULT", "parameters": {}}')
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_component_template_type");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM component_template", Long.class)).isEqualTo(6L);
    }
}
