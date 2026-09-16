package es.codeurjc.infracture.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.codeurjc.infracture.catalog.application.ComponentTemplateService;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;

@SpringBootTest
@Testcontainers
@Transactional
class ComponentTemplateRepositoryTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    private final ComponentTemplateRepository repository;
    private final ComponentTemplateService service;

    @Autowired
    ComponentTemplateRepositoryTests(
            ComponentTemplateRepository repository,
            ComponentTemplateService service) {
        this.repository = repository;
        this.service = service;
    }

    @Test
    void loadsCatalogueSeededByFlyway() {
        List<ComponentTemplate> templates = repository.findAll();

        assertThat(templates)
                .extracting(
                        ComponentTemplate::getKey,
                        ComponentTemplate::getName,
                        ComponentTemplate::getType,
                        ComponentTemplate::isEnabled)
                .containsExactlyInAnyOrder(
                        tuple("http-service", "HTTP Service", ComponentType.HTTP_SERVICE, true),
                        tuple("worker", "Worker", ComponentType.WORKER, true),
                        tuple("load-generator", "Load Generator", ComponentType.LOAD_GENERATOR, true),
                        tuple("postgresql", "PostgreSQL", ComponentType.POSTGRESQL, true),
                        tuple("redis", "Redis", ComponentType.REDIS, true),
                        tuple("rabbitmq", "RabbitMQ", ComponentType.RABBITMQ, true));
    }

    @Test
    void returnsEnabledTemplatesLoadedFromDatabaseOrderedByType() {
        repository.deleteAllInBatch();
        repository.saveAllAndFlush(List.of(
                new ComponentTemplate(
                        "enabled-rabbitmq",
                        "Enabled RabbitMQ",
                        ComponentType.RABBITMQ,
                        true),
                new ComponentTemplate(
                        "enabled-http-service",
                        "Enabled HTTP Service",
                        ComponentType.HTTP_SERVICE,
                        true),
                new ComponentTemplate(
                        "disabled-worker",
                        "Disabled Worker",
                        ComponentType.WORKER,
                        false)));

        List<ComponentTemplate> enabledTemplates = service.getEnabledTemplates();

        assertThat(enabledTemplates)
                .extracting(ComponentTemplate::getKey, ComponentTemplate::getType)
                .containsExactly(
                        tuple("enabled-http-service", ComponentType.HTTP_SERVICE),
                        tuple("enabled-rabbitmq", ComponentType.RABBITMQ));
    }
}
