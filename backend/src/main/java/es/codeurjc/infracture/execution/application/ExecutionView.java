package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.domain.Execution;
import java.util.Map;
import java.util.UUID;

// An execution with everything its API response needs, read inside one transaction: the frozen nodes and
// load are loaded, and the nodes carry what the backend last saw of them.
public record ExecutionView(Execution execution, Map<UUID, Node> nodes, Long secondsLeft) {

    // What was last seen of one node; both are null until the backend has seen the running node.
    public record Node(ComponentStatus status, String response) {

        static final Node NOTHING_SEEN = new Node(null, null);
    }

    public Node node(UUID componentKey) {
        return nodes.getOrDefault(componentKey, Node.NOTHING_SEEN);
    }
}
