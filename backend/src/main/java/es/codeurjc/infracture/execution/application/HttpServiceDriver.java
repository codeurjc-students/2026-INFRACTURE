package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Container;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.HttpProbe;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Runs an HTTP Service node on the demo-app image.
@Component
class HttpServiceDriver implements ComponentDriver {

    private static final int PORT = 8080;
    private static final String DATABASE_PORT = "database";

    private final String image;

    HttpServiceDriver(@Value("${infracture.execution.http-service-image:infracture/demo-app:0.1.0}") String image) {
        this.image = image;
    }

    @Override
    public ComponentType type() {
        return ComponentType.HTTP_SERVICE;
    }

    @Override
    public Set<String> profiles() {
        return Set.of("STATELESS", "DATABASE_CRUD");
    }

    @Override
    public Service service(ExecutedComponent component, Map<String, Dependency> dependencies) {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("APP_ROLE", "http");
        environment.put("APP_MODE", component.getProfile());
        environment.put("COMPONENT_NAME", component.getName());
        environment.put("RESPONSE_DELAY_MS", String.valueOf(component.getParameters().get("responseDelayMs")));
        Map<String, UUID> secrets = new LinkedHashMap<>();
        Dependency database = dependencies.get(DATABASE_PORT);
        if (database != null) {
            environment.put("DATABASE_URL", database.address().url().toString());
            environment.put("DATABASE_USER", database.address().user());
            secrets.put("DATABASE_PASSWORD", database.key());
        }
        return new Service(new Container(image, null, null, List.of()), environment, secrets,
                new HttpProbe(PORT, "/health", 2, 2, 15, 60));
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
