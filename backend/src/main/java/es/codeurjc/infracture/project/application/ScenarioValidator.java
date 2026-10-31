package es.codeurjc.infracture.project.application;

import es.codeurjc.infracture.catalog.application.ContractCatalog;
import es.codeurjc.infracture.project.domain.Component;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.ValidationIssue;
import es.codeurjc.infracture.project.domain.ValidationIssue.Kind;
import es.codeurjc.infracture.project.domain.ValidationIssue.Severity;
import es.codeurjc.infracture.project.domain.ValidationIssue.Target;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ScenarioValidator {

    private final ContractCatalog contracts;

    public ScenarioValidator(ContractCatalog contracts) {
        this.contracts = contracts;
    }

    public List<ValidationIssue> validate(Scenario scenario) {
        return scenario.getComponents().stream().flatMap(this::validate).toList();
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
}
