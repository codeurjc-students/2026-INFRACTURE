package es.codeurjc.infracture.catalog.application;

import java.util.List;

import org.springframework.stereotype.Service;

import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.persistence.ComponentTemplateRepository;

@Service
public class ComponentTemplateService {

    private final ComponentTemplateRepository repository;

    public ComponentTemplateService(ComponentTemplateRepository repository) {
        this.repository = repository;
    }

    public List<ComponentTemplate> getEnabledTemplates() {
        return repository.findAllByEnabledTrueOrderByNameAsc();
    }

}
