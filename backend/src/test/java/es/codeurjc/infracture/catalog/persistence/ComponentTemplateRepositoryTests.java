package es.codeurjc.infracture.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    void returnsOnlyEnabledTemplatesOrderedByName() {
        repository.deleteAllInBatch();
        repository.saveAllAndFlush(List.of(
                new ComponentTemplate(
                        "a-rabbitmq",
                        "Enabled RabbitMQ",
                        ComponentType.RABBITMQ,
                        true),
                new ComponentTemplate(
                        "z-http-service",
                        "Enabled HTTP Service",
                        ComponentType.HTTP_SERVICE,
                        true),
                new ComponentTemplate(
                        "m-redis",
                        "Enabled Redis",
                        ComponentType.REDIS,
                        true),
                new ComponentTemplate(
                        "disabled-worker",
                        "Disabled Worker",
                        ComponentType.WORKER,
                        false)));

        Page<ComponentTemplate> enabledTemplates = service.getEnabledTemplates(PageRequest.of(0, 10), "", null);

        assertThat(enabledTemplates.getContent())
                .extracting(ComponentTemplate::getKey, ComponentTemplate::getType)
                .containsExactly(
                        tuple("z-http-service", ComponentType.HTTP_SERVICE),
                        tuple("a-rabbitmq", ComponentType.RABBITMQ),
                        tuple("m-redis", ComponentType.REDIS));
    }

    @Test
    void filtersBeforePaginatingVariantsWithEqualNamesByKey() {
        repository.deleteAllInBatch();
        repository.saveAllAndFlush(List.of(
                new ComponentTemplate("http-b", "HTTP Service", ComponentType.HTTP_SERVICE, true),
                new ComponentTemplate("http-a", "HTTP Service", ComponentType.HTTP_SERVICE, true),
                new ComponentTemplate("worker", "HTTP Service", ComponentType.WORKER, true),
                new ComponentTemplate("other-http", "Other Service", ComponentType.HTTP_SERVICE, true),
                new ComponentTemplate("http-disabled", "HTTP Service", ComponentType.HTTP_SERVICE, false)));

        Page<ComponentTemplate> firstPage = service.getEnabledTemplates(
                PageRequest.of(0, 1), "  hTtP  ", ComponentType.HTTP_SERVICE);
        Page<ComponentTemplate> secondPage = service.getEnabledTemplates(
                PageRequest.of(1, 1), "  hTtP  ", ComponentType.HTTP_SERVICE);

        assertThat(firstPage.getContent()).extracting(ComponentTemplate::getKey).containsExactly("http-a");
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.getContent()).extracting(ComponentTemplate::getKey).containsExactly("http-b");
        assertThat(secondPage.hasNext()).isFalse();

        Page<ComponentTemplate> withoutType = service.getEnabledTemplates(
                PageRequest.of(0, 10), "  hTtP  ", null);
        assertThat(withoutType.getContent()).extracting(ComponentTemplate::getKey)
                .containsExactly("http-a", "http-b", "worker");
        assertThat(withoutType.getTotalElements()).isEqualTo(3);
    }

}
