package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Container;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Counters;
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
    private static final String DOWNSTREAM_PORT = "downstream";

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
        return Set.of("STATELESS", "DATABASE_CRUD", "DOWNSTREAM_HTTP");
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
        Dependency downstream = dependencies.get(DOWNSTREAM_PORT);
        if (downstream != null) {
            environment.put("DOWNSTREAM_URL", downstream.address().url().toString());
            environment.put("DOWNSTREAM_NAME", downstream.name());
            environment.put("DOWNSTREAM_KEY", downstream.key().toString());
        }
        return new Service(new Container(image, null, null, List.of()), environment, secrets,
                new HttpProbe(PORT, "/health", 2, 2, 15, 60));
    }

    @Override
    public URI evidence(String host) {
        return httpUrl(host);
    }

    @Override
    public Counters counters(String host, ExecutedComponent component) {
        Map<String, String> series = new LinkedHashMap<>();
        series.put("requests", "infracture_requests_total");
        series.put("errors", "infracture_request_errors_total");
        if ("DATABASE_CRUD".equals(component.getProfile())) {
            series.put("database_queries", "infracture_database_queries_total");
        } else if ("DOWNSTREAM_HTTP".equals(component.getProfile())) {
            series.put("downstream_calls", "infracture_downstream_calls_total");
            series.put("downstream_failures", "infracture_downstream_failures_total");
            series.put("downstream_timeouts", "infracture_downstream_timeouts_total");
        }
        return new Counters(URI.create("http://" + host + ":" + PORT + "/metrics"), series);
    }

    @Override
    public URI httpUrl(String host) {
        return URI.create("http://" + host + ":" + PORT + "/");
    }

    @Override
    public Address address(String host) {
        return new Address(httpUrl(host), null);
    }
}
