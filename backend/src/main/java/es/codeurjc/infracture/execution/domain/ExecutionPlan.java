package es.codeurjc.infracture.execution.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// What one execution runs, in Infracture's own terms: a service per node and, optionally, the load. The plan
// compiler builds it from the snapshot and the runtime engine is the only place that turns it into Compose.
// It is stored as evidence of what ran, so it never holds secrets.
public record ExecutionPlan(
        String project,
        Map<String, String> labels,
        List<Node> nodes,
        Load load) {

    public ExecutionPlan {
        labels = ordered(labels);
        nodes = List.copyOf(nodes);
    }

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
            Container container,
            Wiring wiring,
            Probe health,
            // Where the backend asks the running node for its real response, or null when there is nothing to ask.
            URI evidence,
            // Where the backend scrapes Prometheus counters for this node, or null when it publishes none.
            Counters counters,
            Limits limits) {

        public Node {
            Objects.requireNonNull(container, "container");
            Objects.requireNonNull(wiring, "wiring");
        }
    }

    // Where a node exposes its Prometheus counters and which series map to each Infracture counter name.
    public record Counters(URI endpoint, Map<String, String> series) {

        public Counters {
            Objects.requireNonNull(endpoint, "endpoint");
            series = ordered(series);
        }
    }

    public record Container(
            String image,
            List<String> command,
            RunAs user,
            List<String> storage) {

        public Container {
            command = command == null ? null : List.copyOf(command);
            storage = List.copyOf(storage);
            if (!storage.isEmpty() && user == null) {
                throw new IllegalArgumentException("Storage needs a fixed user to own it");
            }
        }
    }

    public record Wiring(
            Map<String, String> environment,
            Map<String, UUID> secrets,
            List<UUID> dependencies) {

        public Wiring {
            environment = ordered(environment);
            secrets = Collections.unmodifiableMap(new LinkedHashMap<>(secrets));
            dependencies = List.copyOf(dependencies);
        }
    }

    public record RunAs(int uid, int gid) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({ @JsonSubTypes.Type(value = HttpProbe.class, name = "http"),
            @JsonSubTypes.Type(value = CommandProbe.class, name = "command") })
    public sealed interface Probe permits HttpProbe, CommandProbe {

        int intervalSeconds();

        int timeoutSeconds();

        int retries();

        int startPeriodSeconds();
    }

    // The service is healthy once GET http://127.0.0.1:port path answers inside its own container.
    public record HttpProbe(int port, String path, int intervalSeconds, int timeoutSeconds, int retries,
            int startPeriodSeconds) implements Probe {
    }

    public record CommandProbe(List<String> command, int intervalSeconds, int timeoutSeconds, int retries,
            int startPeriodSeconds) implements Probe {

        public CommandProbe {
            command = List.copyOf(command);
        }
    }

    public record Limits(double cpus, long memoryBytes, int pids) {
    }

    public record Load(String image, List<String> command, Map<String, String> environment, UUID target,
            Limits limits) {

        public Load {
            command = command == null ? null : List.copyOf(command);
            environment = ordered(environment);
        }
    }
}
