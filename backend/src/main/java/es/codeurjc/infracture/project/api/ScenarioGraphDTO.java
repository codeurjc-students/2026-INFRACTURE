package es.codeurjc.infracture.project.api;

import java.util.List;

public record ScenarioGraphDTO(int revision, List<ComponentDTO> components, List<ValidationIssueDTO> issues) {
}
