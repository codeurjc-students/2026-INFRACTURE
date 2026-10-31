package es.codeurjc.infracture.project.application;

import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.ValidationIssue;
import java.util.List;

public record ScenarioGraph(Scenario scenario, List<ValidationIssue> issues) {
}
