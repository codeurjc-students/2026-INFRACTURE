package es.codeurjc.infracture.catalog.application;

import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.persistence.ComponentTemplateRepository;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ComponentTemplateService {

    private final ComponentTemplateRepository repository;

    public ComponentTemplateService(ComponentTemplateRepository repository) {
        this.repository = repository;
    }

    public Page<ComponentTemplate> getEnabledTemplates(
        Pageable pageable,
        String q,
        ComponentType type
    ) {
        String search = q.trim();

        if (type == null) {
            return repository.findAllByEnabledTrueAndNameContainingIgnoreCaseOrderByNameAscKeyAsc(
                search,
                pageable
            );
        }

        return repository.findAllByEnabledTrueAndTypeAndNameContainingIgnoreCaseOrderByNameAscKeyAsc(
            type,
            search,
            pageable
        );
    }

    public Map<String, ComponentTemplate> getTemplatesByKey(Collection<String> keys) {
        return repository.findAllByKeyIn(keys).stream()
            .collect(Collectors.toMap(ComponentTemplate::getKey, Function.identity()));
    }
}
