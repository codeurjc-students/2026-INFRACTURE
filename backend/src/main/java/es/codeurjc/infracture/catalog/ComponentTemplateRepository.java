package es.codeurjc.infracture.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComponentTemplateRepository extends JpaRepository<ComponentTemplate, Long> {
    List<ComponentTemplate> findAllByEnabledTrue();
}
