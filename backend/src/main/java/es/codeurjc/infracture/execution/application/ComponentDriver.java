package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import java.net.URI;
import java.util.Map;

// What one ComponentType adds to the plan: its image, its variables and how its health is checked.
// The plan compiler adds what every service shares (network, labels, limits and security).
public interface ComponentDriver {

    ComponentType type();

    Map<String, Object> service(ExecutedComponent component);

    // Where the backend asks the running node for its real response, or null
    // when the type has nothing to ask.
    default URI evidence(String serviceName) {
        return null;
    }
}
