package es.codeurjc.infracture.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.persistence.ComponentTemplateRepository;

class ComponentTemplateServiceTests {

    private final ComponentTemplateRepository repository = mock(ComponentTemplateRepository.class);
    private final ComponentTemplateService service = new ComponentTemplateService(repository);

    @Test
    void returnsEnabledTemplatesFromRepository() {
        ComponentTemplate rabbitMq = new ComponentTemplate(
                "rabbitmq",
                "RabbitMQ",
                ComponentType.RABBITMQ,
                true);
        ComponentTemplate httpService = new ComponentTemplate(
                "http-service",
                "HTTP Service",
                ComponentType.HTTP_SERVICE,
                true);
        ComponentTemplate postgresql = new ComponentTemplate(
                "postgresql",
                "PostgreSQL",
                ComponentType.POSTGRESQL,
                true);
        when(repository.findAllByEnabledTrueOrderByNameAsc())
                .thenReturn(List.of(rabbitMq, httpService, postgresql));

        List<ComponentTemplate> result = service.getEnabledTemplates();

        assertThat(result)
                .extracting(ComponentTemplate::getKey, ComponentTemplate::getName, ComponentTemplate::getType)
                .containsExactly(
                        tuple("rabbitmq", "RabbitMQ", ComponentType.RABBITMQ),
                        tuple("http-service", "HTTP Service", ComponentType.HTTP_SERVICE),
                        tuple("postgresql", "PostgreSQL", ComponentType.POSTGRESQL));
        verify(repository).findAllByEnabledTrueOrderByNameAsc();
    }

    @Test
    void returnsEmptyCatalogueWhenNoEnabledTemplatesExist() {
        when(repository.findAllByEnabledTrueOrderByNameAsc()).thenReturn(List.of());

        List<ComponentTemplate> result = service.getEnabledTemplates();

        assertThat(result).isEmpty();
        verify(repository).findAllByEnabledTrueOrderByNameAsc();
    }
}
