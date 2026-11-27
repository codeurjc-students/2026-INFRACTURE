package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Container;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Counters;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Probe;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// What one ComponentType adds to the plan: its image, its variables and how its health is checked.
// The plan compiler adds what every service shares (host name, labels, limits and the start order).
public interface ComponentDriver {

    ComponentType type();

    // The profiles of the type this driver can run. Run refuses a node whose profile is not here.
    Set<String> profiles();

    // dependencies is keyed by port name.
    Service service(ExecutedComponent component, Map<String, Dependency> dependencies);

    default URI evidence(String host) {
        return null;
    }

    default Counters counters(String host, ExecutedComponent component) {
        return null;
    }

    // Where a load sends its HTTP requests, or null when the type does not accept HTTP.
    default URI httpUrl(String host) {
        return null;
    }

    default Address address(String host) {
        return null;
    }

    record Service(Container container, Map<String, String> environment, Map<String, UUID> secrets, Probe health) {
    }

    record Address(URI url, String user) {
    }

    record Dependency(UUID key, Address address) {
    }
}
