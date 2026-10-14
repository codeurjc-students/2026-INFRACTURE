package es.codeurjc.infracture.catalog.persistence;

import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComponentTemplateRepository
    extends JpaRepository<ComponentTemplate, Long>
{
    Page<ComponentTemplate> findAllByEnabledTrueAndNameContainingIgnoreCaseOrderByNameAscKeyAsc(
        String q,
        Pageable pageable
    );

    Page<ComponentTemplate> findAllByEnabledTrueAndTypeAndNameContainingIgnoreCaseOrderByNameAscKeyAsc(
        ComponentType type,
        String q,
        Pageable pageable
    );
}
