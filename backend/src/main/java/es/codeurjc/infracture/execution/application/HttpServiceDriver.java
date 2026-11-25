package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.HttpProbe;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Runs an HTTP Service node on the demo-app image.
@Component
class HttpServiceDriver implements ComponentDriver {

    private static final int PORT = 8080;

    private final String image;

    HttpServiceDriver(@Value("${infracture.execution.http-service-image:infracture/demo-app:0.1.0}") String image) {
        this.image = image;
    }

    @Override
    public ComponentType type() {
        return ComponentType.HTTP_SERVICE;
    }

    @Override
    public Service service(ExecutedComponent component) {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("APP_ROLE", "http");
        environment.put("APP_MODE", component.getProfile());
        environment.put("COMPONENT_NAME", component.getName());
        environment.put("RESPONSE_DELAY_MS", String.valueOf(component.getParameters().get("responseDelayMs")));
        return new Service(image, null, environment, new HttpProbe(PORT, "/health", 2, 2, 15, 60));
    }

    @Override
    public URI evidence(String host) {
        return httpUrl(host);
    }

    @Override
    public URI httpUrl(String host) {
        return URI.create("http://" + host + ":" + PORT + "/");
    }
}
