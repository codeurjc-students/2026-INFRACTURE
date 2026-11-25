package es.codeurjc.infracture.execution.application;

import java.util.Map;
import java.util.UUID;

// What Docker says about a plan: each node by key, and the load. A node whose container no longer exists
// is missing; load is null when its container does not exist (not started yet, or no load at all).
public record RuntimeStatus(Map<UUID, ComponentStatus> nodes, ComponentStatus load) {

    public RuntimeStatus {
        nodes = Map.copyOf(nodes);
    }
}
