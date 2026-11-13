package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.domain.Outcome;
import es.codeurjc.infracture.execution.domain.Phase;
import es.codeurjc.infracture.execution.domain.StopCause;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExecutionDTO(
        Long id,
        Long scenarioId,
        int scenarioRevision,
        Phase phase,
        StopCause stopCause,
        Outcome outcome,
        String failureReason,
        // Failed cleanups in a row since the last Stop, and the last error. They never change the outcome.
        int cleanupFailures,
        String cleanupError,
        // The automatic attempts are spent: the cleanup waits until the owner presses Stop again.
        boolean cleanupRetryNeeded,
        Instant createdAt,
        // Fixed when every node is healthy; null before. The server ends the experiment at this time.
        Instant deadlineAt,
        // Whole seconds until the deadline by the server's clock, or null when no countdown applies.
        Long secondsLeft,
        Instant releasedAt,
        List<ExecutedComponentDTO> components,
        // Null when the scenario had no load.
        ExecutedWorkloadDTO workload,
        Map<String, Object> plan) {
}
