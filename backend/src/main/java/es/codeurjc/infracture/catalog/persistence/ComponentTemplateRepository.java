package es.codeurjc.infracture.catalog.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.codeurjc.infracture.catalog.domain.ComponentTemplate;

public interface ComponentTemplateRepository extends JpaRepository<ComponentTemplate, Long> {
    List<ComponentTemplate> findAllByEnabledTrue();
}
