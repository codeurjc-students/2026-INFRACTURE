package es.codeurjc.infracture.catalog;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ComponentTemplateService {

    private final ComponentTemplateRepository repository;

    public ComponentTemplateService(ComponentTemplateRepository repository) {
        this.repository = repository;
    }

    public List<ComponentTemplate> getEnabledTemplates() {
        return repository.findAllByEnabledTrue()
                .stream()
                .sorted(Comparator.comparing(ComponentTemplate::getType))
                .toList();
    }

}
