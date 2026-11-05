package es.codeurjc.infracture.execution.persistence;

import es.codeurjc.infracture.execution.domain.Execution;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    Optional<Execution> findByReleasedAtIsNull();

    boolean existsByScenarioIdAndReleasedAtIsNull(Long scenarioId);

    Optional<Execution> findFirstByScenarioIdOrderByCreatedAtDescIdDesc(Long scenarioId);
}
