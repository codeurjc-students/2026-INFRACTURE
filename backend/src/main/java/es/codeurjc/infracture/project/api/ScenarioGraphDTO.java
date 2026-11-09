package es.codeurjc.infracture.project.api;

import java.util.List;

public record ScenarioGraphDTO(int revision, List<ComponentDTO> components, List<ConnectionDTO> connections,
        WorkloadDTO workload, List<ValidationIssueDTO> issues) {
}
