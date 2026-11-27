package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.CommandProbe;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Container;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.RunAs;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Runs a temporary PostgreSQL for the experiment. Its data lives in memory and goes with the container
@Component
class PostgresqlDriver implements ComponentDriver {

    private static final int PORT = 5432;
    private static final String DATABASE = "app";
    private static final RunAs POSTGRES = new RunAs(70, 70);
    private static final String DEFAULT_IMAGE =
            "postgres:18.7-alpine@sha256:0115267ab5a509022df9319c77be365cef335adb71556c75f60f5360ee247e71";

    private final String image;

    PostgresqlDriver(@Value("${infracture.execution.postgresql-image:" + DEFAULT_IMAGE + "}") String image) {
        this.image = image;
    }

    @Override
    public ComponentType type() {
        return ComponentType.POSTGRESQL;
    }

    @Override
    public Set<String> profiles() {
        return Set.of("DEFAULT");
    }

    @Override
    public Service service(ExecutedComponent component, Map<String, Dependency> dependencies) {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("POSTGRES_DB", DATABASE);
        environment.put("POSTGRES_USER", DATABASE);
        return new Service(new Container(image, null, POSTGRES, List.of("/var/lib/postgresql", "/var/run/postgresql")),
                environment, Map.of("POSTGRES_PASSWORD", component.getKey()),
                new CommandProbe(List.of("pg_isready", "-h", "127.0.0.1", "-p", String.valueOf(PORT), "-U", DATABASE,
                        "-d", DATABASE), 2, 2, 15, 60));
    }

    @Override
    public Address address(String host) {
        return new Address(URI.create("postgresql://" + host + ":" + PORT + "/" + DATABASE), DATABASE);
    }
}
