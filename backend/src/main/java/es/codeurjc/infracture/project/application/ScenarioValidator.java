package es.codeurjc.infracture.project.application;

import es.codeurjc.infracture.catalog.application.ContractCatalog;
import es.codeurjc.infracture.catalog.domain.Capability;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.domain.ContractViolation;
import es.codeurjc.infracture.catalog.domain.ParameterContract;
import es.codeurjc.infracture.catalog.domain.PortContract;
import es.codeurjc.infracture.catalog.domain.ProfileContract;
import es.codeurjc.infracture.catalog.domain.TemplateContract;
import es.codeurjc.infracture.catalog.domain.WorkloadContract;
import es.codeurjc.infracture.project.domain.Component;
import es.codeurjc.infracture.project.domain.Connection;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.ValidationIssue;
import es.codeurjc.infracture.project.domain.ValidationIssue.Kind;
import es.codeurjc.infracture.project.domain.ValidationIssue.Severity;
import es.codeurjc.infracture.project.domain.ValidationIssue.Target;
import es.codeurjc.infracture.project.domain.Workload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

// Checks a saved scenario against the contracts. It only reports issues; it never blocks saving.
@Service
public class ScenarioValidator {

    private static final Target WORKLOAD = new Target(Kind.WORKLOAD, null);

    private final ContractCatalog contracts;

    public ScenarioValidator(ContractCatalog contracts) {
        this.contracts = contracts;
    }

    public List<ValidationIssue> validate(Scenario scenario) {
        List<ValidationIssue> issues = new ArrayList<>();
        // Connections only hold node keys, so nodes are looked up by key.
        Map<UUID, Component> nodes = new HashMap<>();
        for (Component component : scenario.getComponents()) {
            nodes.put(component.getKey(), component);
        }
        for (Component component : scenario.getComponents()) {
            checkConfiguration(component, issues);
            checkCardinalities(component, scenario, issues);
        }
        for (Connection connection : scenario.getConnections()) {
            checkConnection(connection, nodes.get(connection.sourceKey()), nodes.get(connection.targetKey()), issues);
        }
        findCycles(scenario, nodes, issues);
        findUnconsumedBrokers(scenario, nodes, issues);
        if (nodes.isEmpty()) {
            issues.add(new ValidationIssue(Severity.ERROR, "EMPTY_SCENARIO",
                    "Add at least one component to run the scenario", new Target(Kind.SCENARIO, null), null));
        }
        checkWorkload(scenario, nodes, issues);
        return issues;
    }

    private void checkConfiguration(Component component, List<ValidationIssue> issues) {
        Target target = componentTarget(component);
        TemplateContract contract = contracts.find(component.getType()).orElse(null);
        if (contract == null) {
            issues.add(new ValidationIssue(Severity.ERROR, "UNKNOWN_TYPE", component.getType() + " has no contract",
                    target, null));
            return;
        }
        for (ContractViolation violation : contract.check(component.getConfiguration())) {
            issues.add(new ValidationIssue(Severity.ERROR, violation.code(), violation.message(), target,
                    violation.field()));
        }
    }

    // Each port of the profile needs between its minimum and maximum outgoing
    // connections.
    private void checkCardinalities(Component component, Scenario scenario, List<ValidationIssue> issues) {
        ProfileContract profile = profile(component);
        if (profile == null) {
            return;
        }
        for (PortContract port : profile.ports()) {
            int count = 0;
            for (Connection connection : scenario.connectionsFrom(component.getKey())) {
                if (connection.port().equals(port.name())) {
                    count++;
                }
            }
            if (count < port.min()) {
                issues.add(new ValidationIssue(Severity.ERROR, "MISSING_CONNECTION",
                        "%s needs at least %d connection on port %s".formatted(component.getName(), port.min(),
                                port.name()),
                        componentTarget(component), port.name()));
            } else if (count > port.max()) {
                issues.add(new ValidationIssue(Severity.ERROR, "TOO_MANY_CONNECTIONS",
                        "%s accepts at most %d connection on port %s".formatted(component.getName(), port.max(),
                                port.name()),
                        componentTarget(component), port.name()));
            }
        }
    }

    // A node without a known profile already has its own issue, so its connections
    // are not judged.
    private void checkConnection(Connection connection, Component source, Component target,
            List<ValidationIssue> issues) {
        ProfileContract sourceProfile = profile(source);
        if (sourceProfile == null) {
            return;
        }
        String port = connection.port();
        PortContract portContract = null;
        for (PortContract candidate : sourceProfile.ports()) {
            if (candidate.name().equals(port)) {
                portContract = candidate;
                break;
            }
        }
        if (portContract == null) {
            issues.add(new ValidationIssue(Severity.ERROR, "UNKNOWN_PORT", "%s has no port %s in profile %s"
                    .formatted(source.getName(), port, sourceProfile.name()), connectionTarget(connection), port));
            return;
        }
        ProfileContract targetProfile = profile(target);
        if (targetProfile == null) {
            return;
        }
        for (Capability capability : portContract.accepts()) {
            if (targetProfile.capabilities().contains(capability)) {
                return;
            }
        }
        String needs = String.join(" or ", portContract.accepts().stream().map(Capability::name).toList());
        issues.add(new ValidationIssue(Severity.ERROR, "CAPABILITY_MISMATCH",
                "Port %s of %s needs %s, which %s does not offer".formatted(port, source.getName(), needs,
                        target.getName()),
                connectionTarget(connection), port));
    }

    // Depth-first search from every node: a connection that reaches a node on the
    // current path closes a cycle.
    private static void findCycles(Scenario scenario, Map<UUID, Component> nodes, List<ValidationIssue> issues) {
        Set<UUID> visited = new HashSet<>();
        Set<UUID> onPath = new HashSet<>();
        for (Component component : scenario.getComponents()) {
            visit(component.getKey(), scenario, nodes, visited, onPath, issues);
        }
    }

    private static void visit(UUID key, Scenario scenario, Map<UUID, Component> nodes, Set<UUID> visited,
            Set<UUID> onPath, List<ValidationIssue> issues) {

        if (visited.contains(key)) {
            return;
        }
        visited.add(key);
        onPath.add(key);
        for (Connection connection : scenario.connectionsFrom(key)) {
            if (onPath.contains(connection.targetKey())) {
                issues.add(new ValidationIssue(Severity.ERROR, "DEPENDENCY_CYCLE", "%s → %s closes a dependency cycle"
                        .formatted(nodes.get(key).getName(), nodes.get(connection.targetKey()).getName()),
                        connectionTarget(connection), connection.port()));
            } else {
                visit(connection.targetKey(), scenario, nodes, visited, onPath, issues);
            }
        }
        onPath.remove(key);
    }

    // A RabbitMQ that something publishes to but no Worker reads from accumulates
    // tasks.
    // shortcut: a Worker is the only consumer type today; if another type consumes,
    // this check must know it.
    private static void findUnconsumedBrokers(Scenario scenario, Map<UUID, Component> nodes,
            List<ValidationIssue> issues) {
        for (Component broker : scenario.getComponents()) {
            if (broker.getType() != ComponentType.RABBITMQ) {
                continue;
            }
            boolean published = false;
            boolean consumed = false;
            for (Connection connection : scenario.getConnections()) {
                if (!connection.targetKey().equals(broker.getKey())) {
                    continue;
                }
                if (nodes.get(connection.sourceKey()).getType() == ComponentType.WORKER) {
                    consumed = true;
                } else {
                    published = true;
                }
            }
            if (published && !consumed) {
                issues.add(new ValidationIssue(Severity.WARNING, "NO_CONSUMER",
                        "Tasks published to %s accumulate because no Worker consumes them".formatted(broker.getName()),
                        componentTarget(broker), null));
            }
        }
    }

    // The load is optional: without one the scenario runs with no traffic
    private void checkWorkload(Scenario scenario, Map<UUID, Component> nodes, List<ValidationIssue> issues) {
        Optional<Workload> found = scenario.getWorkload();
        if (found.isEmpty()) {
            issues.add(new ValidationIssue(Severity.WARNING, "NO_WORKLOAD",
                    "The scenario has no load, so the experiment receives no traffic", WORKLOAD, null));
            return;
        }
        Workload workload = found.get();
        WorkloadContract contract = null;
        if (workload.profile() == null) {
            issues.add(new ValidationIssue(Severity.ERROR, "MISSING_PROFILE", "The load needs a profile", WORKLOAD,
                    "profile"));
        } else {
            contract = WorkloadContract.find(workload.profile()).orElse(null);
            if (contract == null) {
                issues.add(new ValidationIssue(Severity.ERROR, "UNKNOWN_PROFILE",
                        "There is no load profile " + workload.profile(), WORKLOAD, "profile"));
            } else {
                for (ContractViolation violation : ParameterContract.check(contract.parameters(),
                        workload.parameters(), contract.profile())) {
                    issues.add(new ValidationIssue(Severity.ERROR, violation.code(), violation.message(), WORKLOAD,
                            violation.field()));
                }
            }
        }
        for (ContractViolation violation : ParameterContract.check(List.of(WorkloadContract.SEED),
                Map.of(WorkloadContract.SEED.name(), workload.seed()), workload.profile())) {
            issues.add(new ValidationIssue(Severity.ERROR, violation.code(), violation.message(), WORKLOAD,
                    violation.field()));
        }
        if (workload.targetKey() == null) {
            issues.add(new ValidationIssue(Severity.ERROR, "WORKLOAD_WITHOUT_TARGET",
                    "Choose the node that receives the load", WORKLOAD, "targetKey"));
            return;
        }
        Component target = nodes.get(workload.targetKey());
        ProfileContract targetProfile = profile(target);
        // A target without a known profile already has its own issue.
        if (contract != null && targetProfile != null
                && !targetProfile.capabilities().containsAll(contract.requires())) {
            String needs = String.join(" and ", contract.requires().stream().map(Capability::name).toList());
            issues.add(new ValidationIssue(Severity.ERROR, "CAPABILITY_MISMATCH",
                    "The load %s needs %s, which %s does not offer".formatted(contract.profile(), needs,
                            target.getName()),
                    WORKLOAD, "targetKey"));
        }
    }

    // The profile the node uses, or null when its type or profile is unknown.
    private ProfileContract profile(Component component) {
        TemplateContract contract = contracts.find(component.getType()).orElse(null);
        return contract == null ? null : contract.profile(component.getConfiguration().profile()).orElse(null);
    }

    private static Target componentTarget(Component component) {
        return new Target(Kind.COMPONENT, component.getKey().toString());
    }

    private static Target connectionTarget(Connection connection) {
        return new Target(Kind.CONNECTION,
                connection.sourceKey() + ":" + connection.port() + ":" + connection.targetKey());
    }
}
