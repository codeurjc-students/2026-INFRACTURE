package es.codeurjc.infracture.project.persistence;

import es.codeurjc.infracture.project.domain.Scenario;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    Page<Scenario> findAllByProjectIdOrderByCreatedAtDescIdDesc(Long projectId, Pageable pageable);

    Optional<Scenario> findByIdAndProjectId(Long id, Long projectId);
}
