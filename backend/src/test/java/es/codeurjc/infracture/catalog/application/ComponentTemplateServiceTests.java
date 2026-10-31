package es.codeurjc.infracture.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.persistence.ComponentTemplateRepository;

class ComponentTemplateServiceTests {

    private final ComponentTemplateRepository repository = mock(ComponentTemplateRepository.class);
    private final ComponentTemplateService service = new ComponentTemplateService(repository);

    @Test
    void returnsEmptyPageWithoutATypeFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ComponentTemplate> result = Page.empty(pageable);
        when(repository.findAllByEnabledTrueAndNameContainingIgnoreCaseOrderByNameAscKeyAsc("", pageable))
                .thenReturn(result);

        assertThat(service.getEnabledTemplates(pageable, "   ", null)).isSameAs(result);
    }

    @Test
    void trimsSearchAndReturnsPageForRequestedType() {
        Pageable pageable = PageRequest.of(0, 10);
        ComponentTemplate redis = new ComponentTemplate("redis", "Redis", ComponentType.REDIS, true, new ComponentConfiguration("DEFAULT", Map.of()));
        Page<ComponentTemplate> result = new PageImpl<>(List.of(redis), pageable, 1);
        when(repository.findAllByEnabledTrueAndTypeAndNameContainingIgnoreCaseOrderByNameAscKeyAsc(
                ComponentType.REDIS, "redis", pageable))
                .thenReturn(result);

        assertThat(service.getEnabledTemplates(pageable, "  redis  ", ComponentType.REDIS)).isSameAs(result);
    }
}
