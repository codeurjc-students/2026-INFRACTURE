package es.codeurjc.infracture.execution.persistence;

import es.codeurjc.infracture.execution.domain.Execution;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    Optional<Execution> findByReleasedAtIsNull();

    // Stop and the reconciler both change the active execution. The row lock makes one wait for the
    // other, so neither overwrites the cause or phase the other just wrote.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Execution> findForUpdateByReleasedAtIsNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Execution> findForUpdateById(Long id);

    boolean existsByScenarioIdAndReleasedAtIsNull(Long scenarioId);

    Optional<Execution> findFirstByScenarioIdOrderByCreatedAtDescIdDesc(Long scenarioId);
}
