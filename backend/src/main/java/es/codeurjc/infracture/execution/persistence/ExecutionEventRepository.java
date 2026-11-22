package es.codeurjc.infracture.execution.persistence;

import es.codeurjc.infracture.execution.domain.ExecutionEvent;
import es.codeurjc.infracture.execution.domain.ExecutionEvent.Kind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionEventRepository extends JpaRepository<ExecutionEvent, Long> {

    // The next page an observer receives: at most 200 events after its cursor, in order.
    List<ExecutionEvent> findTop200ByExecutionIdAndIdGreaterThanOrderById(Long executionId, long after);

    // States and lifecycle events, without the log lines, to know what was last seen of each node.
    List<ExecutionEvent> findByExecutionIdAndKindNotOrderById(Long executionId, Kind kind);

    boolean existsByIdAndExecutionId(Long id, Long executionId);

    Optional<ExecutionEvent> findFirstByExecutionIdOrderByIdDesc(Long executionId);

    Optional<ExecutionEvent> findFirstByExecutionIdAndKindOrderByAtDescIdDesc(Long executionId, Kind kind);

    long countByExecutionIdAndComponentKeyAndKind(Long executionId, UUID componentKey, Kind kind);

    long countByExecutionIdAndComponentKeyIsNullAndKind(Long executionId, Kind kind);
}
