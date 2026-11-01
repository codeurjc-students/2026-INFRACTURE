package es.codeurjc.infracture.project.application;

import es.codeurjc.infracture.catalog.application.ContractCatalog;
import es.codeurjc.infracture.catalog.domain.Capability;
import es.codeurjc.infracture.catalog.domain.PortContract;
import es.codeurjc.infracture.catalog.domain.ProfileContract;
import es.codeurjc.infracture.project.domain.Component;
import es.codeurjc.infracture.project.domain.Connection;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.ValidationIssue;
import es.codeurjc.infracture.project.domain.ValidationIssue.Kind;
import es.codeurjc.infracture.project.domain.ValidationIssue.Severity;
import es.codeurjc.infracture.project.domain.ValidationIssue.Target;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ScenarioValidator {

    private final ContractCatalog contracts;

    public ScenarioValidator(ContractCatalog contracts) {
        this.contracts = contracts;
    }

    public List<ValidationIssue> validate(Scenario scenario) {
        Map<UUID, Component> components = scenario.getComponents().stream()
                .collect(Collectors.toMap(Component::getKey, Function.identity()));
        return Stream.concat(
                scenario.getComponents().stream().flatMap(this::validate),
                scenario.getConnections().stream().flatMap(connection -> validate(connection,
                        components.get(connection.sourceKey()), components.get(connection.targetKey()))))
                .toList();
    }

    private Stream<ValidationIssue> validate(Component component) {
        Target target = new Target(Kind.COMPONENT, component.getKey().toString());
        return contracts.find(component.getType())
                .map(contract -> contract.check(component.getConfiguration()).stream()
                        .map(violation -> new ValidationIssue(Severity.ERROR, violation.code(), violation.message(),
                                target, violation.field())))
                .orElseGet(() -> Stream.of(new ValidationIssue(Severity.ERROR, "UNKNOWN_TYPE",
                        component.getType() + " has no contract", target, null)));
    }

    // A node without a known profile already has its own issue, so its connections are not judged.
    private Stream<ValidationIssue> validate(Connection connection, Component source, Component target) {
        Optional<ProfileContract> sourceProfile = profile(source);
        if (sourceProfile.isEmpty()) {
            return Stream.empty();
        }
        String port = connection.port();
        Target issueTarget = new Target(Kind.CONNECTION,
                connection.sourceKey() + ":" + port + ":" + connection.targetKey());
        Optional<PortContract> portContract = sourceProfile.get().ports().stream()
                .filter(candidate -> candidate.name().equals(port)).findFirst();
        if (portContract.isEmpty()) {
            return Stream.of(new ValidationIssue(Severity.ERROR, "UNKNOWN_PORT", "%s has no port %s in profile %s"
                    .formatted(source.getName(), port, sourceProfile.get().name()), issueTarget, port));
        }
        Optional<ProfileContract> targetProfile = profile(target);
        List<Capability> accepts = portContract.get().accepts();
        if (targetProfile.isEmpty() || accepts.stream().anyMatch(targetProfile.get().capabilities()::contains)) {
            return Stream.empty();
        }
        String needs = accepts.stream().map(Capability::name).collect(Collectors.joining(" or "));
        return Stream.of(new ValidationIssue(Severity.ERROR, "CAPABILITY_MISMATCH",
                "Port %s of %s needs %s, which %s does not offer".formatted(port, source.getName(), needs,
                        target.getName()), issueTarget, port));
    }

    private Optional<ProfileContract> profile(Component component) {
        return contracts.find(component.getType())
                .flatMap(contract -> contract.profile(component.getConfiguration().profile()));
    }
}
