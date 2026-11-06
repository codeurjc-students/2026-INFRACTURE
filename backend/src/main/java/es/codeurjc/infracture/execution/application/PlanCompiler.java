package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.Execution;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    // Compose accepts lowercase letters, digits, "-" and "_" in project names.
    private static final Pattern INSTANCE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,39}");

    private final Map<ComponentType, ComponentDriver> drivers = new EnumMap<>(ComponentType.class);
    private final String instance;

    public PlanCompiler(List<ComponentDriver> drivers, @Value("${infracture.instance-id:local}") String instance) {
        for (ComponentDriver driver : drivers) {
            if (this.drivers.put(driver.type(), driver) != null) {
                throw new IllegalStateException(driver.type() + " has more than one driver");
            }
        }
        if (!INSTANCE_ID.matcher(instance).matches()) {
            throw new IllegalStateException("infracture.instance-id must be 1-40 lowercase letters, digits, - or _");
        }
        this.instance = instance;
    }

    // Execution ids restart in every installation, so two instances sharing one
    // Docker engine (for example the integration tests and a developer's own
    // backend) only stay apart if the
    // project name includes the instance.
    public String projectName(Long executionId) {
        return "infracture-" + instance + "-exec-" + executionId;
    }

    public Map<String, Object> compile(Execution execution) {
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put(INSTANCE_LABEL, instance);
        labels.put(EXECUTION_LABEL, String.valueOf(execution.getId()));

        Map<String, Object> services = new LinkedHashMap<>();
        for (ExecutedComponent component : execution.getComponents()) {
            services.put(serviceName(component), service(component, labels));
        }

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("name", projectName(execution.getId()));
        plan.put("networks", Map.of(NETWORK, Map.of("labels", labels)));
        plan.put("services", services);
        return plan;
    }

    private Map<String, Object> service(ExecutedComponent component, Map<String, Object> executionLabels) {
        ComponentDriver driver = drivers.get(component.getType());
        if (driver == null) {
            throw new IllegalStateException("No driver runs " + component.getType());
        }
        Map<String, Object> labels = new LinkedHashMap<>(executionLabels);
        labels.put(COMPONENT_LABEL, component.getKey().toString());

        Map<String, Object> service = new LinkedHashMap<>(driver.service(component));
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

    private static String serviceName(ExecutedComponent component) {
        return "c-" + component.getKey();
    }
}
