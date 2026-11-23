package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.domain.ExecutionEvent;
import es.codeurjc.infracture.execution.persistence.ExecutionEventRepository;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.identity.application.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// What an observer of one execution receives next (#92). Each open stream asks it when the execution has
// new events and at least once per heartbeat. Everything comes from the database, so several backends
// could serve the same execution.
@Service
public class ExecutionFeed {

    private final ExecutionEventRepository events;
    private final ExecutionRepository executions;
    private final UserService users;

    public ExecutionFeed(ExecutionEventRepository events, ExecutionRepository executions, UserService users) {
        this.events = events;
        this.executions = executions;
        this.users = users;
    }

    // The events after the cursor, whether the execution has ended, and whether the session may still watch:
    // a deactivated or deleted account, or a revoked session, loses its stream at its next read.
    public record Batch(List<ExecutionEvent> events, boolean released, boolean allowed) {
    }

    @Transactional(readOnly = true)
    public Batch next(Long userId, int tokenVersion, Long executionId, long after) {
        if (!users.isSessionCurrent(userId, tokenVersion)) {
            return new Batch(List.of(), false, false);
        }
        boolean released = executions.findById(executionId).map(execution -> !execution.isActive()).orElse(true);
        return new Batch(events.findTop200ByExecutionIdAndIdGreaterThanOrderById(executionId, after), released, true);
    }

    // A cursor names an event of this execution that is still kept; any other cursor needs a reset.
    @Transactional(readOnly = true)
    public boolean isKnown(Long executionId, long cursor) {
        return events.existsByIdAndExecutionId(cursor, executionId);
    }

    // The cursor of the newest event, or 0 when there is none yet.
    @Transactional(readOnly = true)
    public long latest(Long executionId) {
        return events.findFirstByExecutionIdOrderByIdDesc(executionId).map(ExecutionEvent::getId).orElse(0L);
    }
}
