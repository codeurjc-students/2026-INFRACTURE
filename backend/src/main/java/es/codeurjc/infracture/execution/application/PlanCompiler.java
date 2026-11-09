package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.ExecutedWorkload;
import es.codeurjc.infracture.execution.domain.Execution;
import java.net.URI;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Translates a reserved execution into a Compose-shaped plan. It never touches Docker, so the same
// plan can be stored as evidence of what ran and tested without Docker. It holds no secrets.
@Component
public class PlanCompiler {

    public static final String INSTANCE_LABEL = "io.infracture.instance";
    public static final String EXECUTION_LABEL = "io.infracture.execution";
    public static final String COMPONENT_LABEL = "io.infracture.component";
    public static final String NETWORK = "exec";
    // The k6 service. Its Compose profile keeps `up --wait` from starting it with
    // the nodes.
    public static final String LOAD_SERVICE = "workload";
    public static final String LOAD_PROFILE = "load";
    // Compose accepts lowercase letters, digits, "-" and "_" in project names.
    private static final Pattern INSTANCE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,39}");

    private final Map<ComponentType, ComponentDriver> drivers = new EnumMap<>(ComponentType.class);
    private final String instance;
    private final String loadImage;

    public PlanCompiler(List<ComponentDriver> drivers, @Value("${infracture.instance-id:local}") String instance,
            @Value("${infracture.execution.load-generator-image:infracture/load-generator:0.1.0}") String loadImage) {
        for (ComponentDriver driver : drivers) {
            if (this.drivers.put(driver.type(), driver) != null) {
                throw new IllegalStateException(driver.type() + " has more than one driver");
            }
        }
        if (!INSTANCE_ID.matcher(instance).matches()) {
            throw new IllegalStateException("infracture.instance-id must be 1-40 lowercase letters, digits, - or _");
        }
        this.instance = instance;
        this.loadImage = loadImage;
    }

    // Execution ids restart in every installation, so two instances sharing one
    // Docker engine (for example the integration tests and a developer's own
    // backend) only stay apart if the
    // project name includes the instance.
    public String projectName(Long executionId) {
        return "infracture-" + instance + "-exec-" + executionId;
    }

    // Every resource of the execution carries these labels; cleanup filters by
    // both.
    public Map<String, String> labels(Long executionId) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(INSTANCE_LABEL, instance);
        labels.put(EXECUTION_LABEL, String.valueOf(executionId));
        return labels;
    }

    public Map<String, Object> compile(Execution execution) {
        Map<String, String> labels = labels(execution.getId());

        Map<String, Object> services = new LinkedHashMap<>();
        for (ExecutedComponent component : execution.getComponents()) {
            services.put(serviceName(component), service(component, labels));
        }
        if (execution.getWorkload() != null) {
            services.put(LOAD_SERVICE, load(execution, labels));
        }

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("name", projectName(execution.getId()));
        plan.put("networks", Map.of(NETWORK, Map.of("labels", labels)));
        plan.put("services", services);
        return plan;
    }

    private Map<String, Object> service(ExecutedComponent component, Map<String, String> executionLabels) {
        ComponentDriver driver = drivers.get(component.getType());
        if (driver == null) {
            throw new IllegalStateException("No driver runs " + component.getType());
        }
        Map<String, String> labels = new LinkedHashMap<>(executionLabels);
        labels.put(COMPONENT_LABEL, component.getKey().toString());
        return confined(driver.service(component), labels);
    }

    // k6 with the fixed script of the profile. It only receives the target URL and
    // numbers.
    private Map<String, Object> load(Execution execution, Map<String, String> labels) {
        ExecutedWorkload workload = execution.getWorkload();
        ExecutedComponent target = execution.getComponents().stream()
                .filter(component -> component.getKey().equals(workload.getTargetKey())).findFirst().orElseThrow();
        ComponentDriver driver = drivers.get(target.getType());
        URI url = driver == null ? null : driver.httpUrl(serviceName(target));
        if (url == null) {
            throw new IllegalStateException(target.getType() + " does not accept HTTP load");
        }
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("WORKLOAD_PROFILE", workload.getProfile());
        environment.put("TARGET_URL", url.toString());
        new TreeMap<>(workload.getParameters())
                .forEach((name, value) -> environment.put(environmentName(name), String.valueOf(value)));
        environment.put("RANDOM_SEED", String.valueOf(workload.getSeed()));

        Map<String, Object> service = new LinkedHashMap<>();
        service.put("image", loadImage);
        service.put("environment", environment);
        service.put("profiles", List.of(LOAD_PROFILE));
        service.put("depends_on", Map.of(serviceName(target), Map.of("condition", "service_healthy")));
        return confined(service, labels);
    }

    // What every service shares: the execution network and labels, limits and no
    // privileges.
    private static Map<String, Object> confined(Map<String, Object> driverService, Map<String, String> labels) {
        Map<String, Object> service = new LinkedHashMap<>(driverService);
        service.put("networks", List.of(NETWORK));
        service.put("labels", labels);
        service.put("read_only", true);
        service.put("tmpfs", List.of("/tmp"));
        service.put("cap_drop", List.of("ALL"));
        service.put("security_opt", List.of("no-new-privileges:true"));
        service.put("deploy",
                Map.of("resources", Map.of("limits", Map.of("cpus", "0.5", "memory", "384M", "pids", 200))));
        return service;
    }

    // The URL of each node whose driver can show a real response.
    public Map<UUID, URI> evidence(Execution execution) {
        Map<UUID, URI> uris = new LinkedHashMap<>();
        for (ExecutedComponent component : execution.getComponents()) {
            ComponentDriver driver = drivers.get(component.getType());
            URI uri = driver == null ? null : driver.evidence(serviceName(component));
            if (uri != null) {
                uris.put(component.getKey(), uri);
            }
        }
        return uris;
    }

    // requestsPerSecond -> REQUESTS_PER_SECOND
    private static String environmentName(String parameter) {
        return parameter.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    static String serviceName(ExecutedComponent component) {
        return "c-" + component.getKey();
    }
}
