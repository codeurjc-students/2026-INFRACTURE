package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.application.ComponentDriver.Address;
import es.codeurjc.infracture.execution.application.ComponentDriver.Dependency;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.ExecutedWorkload;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Limits;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Load;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Node;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Wiring;
import es.codeurjc.infracture.project.domain.Connection;
import java.net.URI;
import java.util.ArrayList;
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

// Translates a reserved execution into its ExecutionPlan. It never touches Docker, so the same plan can be
// stored as evidence of what ran and tested without Docker. It holds no secrets and no Compose syntax.
@Component
public class PlanCompiler {

    public static final String INSTANCE_LABEL = "io.infracture.instance";
    public static final String EXECUTION_LABEL = "io.infracture.execution";
    public static final String COMPONENT_LABEL = "io.infracture.component";
    // Every service, node or load, gets the same limits for now (#107 measures them).
    private static final Limits LIMITS = new Limits(0.5, 384L << 20, 200);
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

    // Whether a driver declares this profile of the type. Run asks this before reserving.
    public boolean runs(ComponentType type, String profile) {
        ComponentDriver driver = drivers.get(type);
        return driver != null && driver.profiles().contains(profile);
    }

    public ExecutionPlan compile(Execution execution) {
        List<Node> nodes = new ArrayList<>();
        for (ExecutedComponent component : execution.getComponents()) {
            nodes.add(node(execution, component));
        }
        ExecutedWorkload workload = execution.getWorkload();
        Load load = workload == null ? null : load(execution, workload);
        return new ExecutionPlan(projectName(execution.getId()), labels(execution.getId()), nodes, load);
    }

    private Node node(Execution execution, ExecutedComponent component) {
        ComponentDriver driver = driver(component);
        Map<String, Dependency> dependencies = dependencies(execution, component);
        ComponentDriver.Service service = driver.service(component, dependencies);
        String host = host(component);
        List<UUID> targets = dependencies.values().stream().map(Dependency::key).distinct().toList();
        return new Node(component.getKey(), host, service.container(),
                new Wiring(service.environment(), service.secrets(), targets), service.health(), driver.evidence(host),
                driver.counters(host, component), LIMITS);
    }

    // The validator already checked that every connection reaches a node that offers what the port needs.
    private Map<String, Dependency> dependencies(Execution execution, ExecutedComponent component) {
        Map<String, Dependency> dependencies = new TreeMap<>();
        for (Connection connection : execution.getConnections()) {
            if (connection.sourceKey().equals(component.getKey())) {
                ExecutedComponent target = component(execution, connection.targetKey());
                Address address = driver(target).address(host(target));
                if (address == null) {
                    throw new IllegalStateException(target.getType() + " cannot be connected to");
                }
                dependencies.put(connection.port(), new Dependency(target.getKey(), target.getName(), address));
            }
        }
        return dependencies;
    }

    // k6 with the fixed script of the profile
    private Load load(Execution execution, ExecutedWorkload workload) {
        ExecutedComponent target = component(execution, workload.getTargetKey());
        ComponentDriver driver = drivers.get(target.getType());
        URI url = driver == null ? null : driver.httpUrl(host(target));
        if (url == null) {
            throw new IllegalStateException(target.getType() + " does not accept HTTP load");
        }
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("WORKLOAD_PROFILE", workload.getProfile());
        environment.put("TARGET_URL", url.toString());
        new TreeMap<>(workload.getParameters())
                .forEach((name, value) -> environment.put(environmentName(name), String.valueOf(value)));
        environment.put("RANDOM_SEED", String.valueOf(workload.getSeed()));
        return new Load(loadImage, null, environment, target.getKey(), LIMITS);
    }

    private static ExecutedComponent component(Execution execution, UUID key) {
        return execution.getComponents().stream().filter(component -> component.getKey().equals(key)).findFirst()
                .orElseThrow();
    }

    private ComponentDriver driver(ExecutedComponent component) {
        if (!runs(component.getType(), component.getProfile())) {
            throw new IllegalStateException("No driver runs %s %s".formatted(component.getType(),
                    component.getProfile()));
        }
        return drivers.get(component.getType());
    }

    // requestsPerSecond -> REQUESTS_PER_SECOND
    private static String environmentName(String parameter) {
        return parameter.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    // The name a node answers to on the execution network. The node key keeps it unique and free of user text.
    private static String host(ExecutedComponent component) {
        return "c-" + component.getKey();
    }
}
