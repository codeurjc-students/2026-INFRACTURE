package es.codeurjc.infracture.execution.application;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// What the reconciler last saw of each node: its status and its real response.
// It lives in memory because it only matters while the experiment runs, and a
// backend restart ends the experiment anyway.
// shortcut: P08 stores events and samples in the database instead.
@Component
public class ExecutionObservations {

    public record Observation(ComponentStatus status, String response) {
    }

    private static final Observation NOTHING_SEEN = new Observation(null, null);

    private final Map<Long, Map<UUID, Observation>> byExecution = new ConcurrentHashMap<>();

    public Observation of(Long executionId, UUID componentKey) {
        Map<UUID, Observation> seen = byExecution.get(executionId);
        if (seen == null) {
            return NOTHING_SEEN;
        }
        return seen.getOrDefault(componentKey, NOTHING_SEEN);
    }

    // Only the reconciler writes, from its single thread, so reading an entry
    // and then replacing it never races with another write.
    void recordStatus(Long executionId, UUID componentKey, ComponentStatus status) {
        Map<UUID, Observation> nodes = nodes(executionId);
        Observation old = nodes.getOrDefault(componentKey, NOTHING_SEEN);
        nodes.put(componentKey, new Observation(status, old.response()));
    }

    void recordResponse(Long executionId, UUID componentKey, String response) {
        Map<UUID, Observation> nodes = nodes(executionId);
        Observation old = nodes.getOrDefault(componentKey, NOTHING_SEEN);
        nodes.put(componentKey, new Observation(old.status(), response));
    }

    void forget(Long executionId) {
        byExecution.remove(executionId);
    }

    private Map<UUID, Observation> nodes(Long executionId) {
        return byExecution.computeIfAbsent(executionId, id -> new ConcurrentHashMap<>());
    }
}
