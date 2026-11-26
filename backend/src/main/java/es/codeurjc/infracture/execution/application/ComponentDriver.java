package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.HttpProbe;
import java.net.URI;
import java.util.List;
import java.util.Map;

// What one ComponentType adds to the plan: its image, its variables and how its health is checked.
// The plan compiler adds what every service shares (host name, labels and limits).
public interface ComponentDriver {

    ComponentType type();

    Service service(ExecutedComponent component);

    // Where the backend asks the running node for its real response, or null
    // when the type has nothing to ask.
    default URI evidence(String host) {
        return null;
    }

    // Where a load sends its HTTP requests, or null when the type does not accept HTTP.
    default URI httpUrl(String host) {
        return null;
    }

    // command is fixed by the driver, or null to run the image's own.
    record Service(String image, List<String> command, Map<String, String> environment, HttpProbe health) {
    }
}
