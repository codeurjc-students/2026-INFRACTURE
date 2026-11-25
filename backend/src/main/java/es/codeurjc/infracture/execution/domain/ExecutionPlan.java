package es.codeurjc.infracture.execution.domain;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// What one execution runs, in Infracture's own terms: a service per node and, optionally, the load. The plan
// compiler builds it from the snapshot and the runtime engine is the only place that turns it into Compose.
// It is stored as evidence of what ran, so it never holds secrets.
public record ExecutionPlan(
        // Names every runtime resource of the execution, so two instances sharing one Docker stay apart.
        String project,
        // Every resource of the execution carries them; cleanup removes what carries all of them.
        Map<String, String> labels,
        List<Node> nodes,
        // Null when the scenario has no load.
        Load load) {

    public ExecutionPlan {
        labels = ordered(labels);
        nodes = List.copyOf(nodes);
    }

    // Keeps the order the compiler wrote, so the same snapshot always renders the same text.
    private static Map<String, String> ordered(Map<String, String> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    public Node node(UUID key) {
        return nodes.stream().filter(node -> node.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The plan has no node " + key));
    }

    // One node of the canvas as a running service.
    public record Node(
            UUID key,
            // The name the node answers to on the execution network.
            String host,
            String image,
            // Fixed by the driver, never typed by the user (ADR 0030); null runs the image's own command.
            List<String> command,
            Map<String, String> environment,
            HttpProbe health,
            // Where the backend asks the running node for its real response, or null when there is nothing to ask.
            URI evidence,
            Limits limits) {

        public Node {
            command = command == null ? null : List.copyOf(command);
            environment = ordered(environment);
        }
    }

    // The service is healthy once GET http://127.0.0.1:port path answers inside its own container.
    public record HttpProbe(int port, String path, int intervalSeconds, int timeoutSeconds, int retries,
            int startPeriodSeconds) {
    }

    // What one service may consume. CPU in cores, so 0.5 is half a core.
    public record Limits(double cpus, long memoryBytes, int pids) {
    }

    // The load generator. It starts only once every node is healthy, and only after its target.
    public record Load(String image, List<String> command, Map<String, String> environment, UUID target,
            Limits limits) {

        public Load {
            command = command == null ? null : List.copyOf(command);
            environment = ordered(environment);
        }
    }
}
