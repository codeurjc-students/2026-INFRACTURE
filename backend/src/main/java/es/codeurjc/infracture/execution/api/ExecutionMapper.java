package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.application.ExecutionObservations;
import es.codeurjc.infracture.execution.application.ExecutionObservations.Observation;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.Execution;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ExecutionMapper {

    // Joins the frozen nodes with what the reconciler last saw of each one.
    default ExecutionDTO toDTO(Execution execution, ExecutionObservations observations) {
        List<ExecutedComponentDTO> components = new ArrayList<>();
        for (ExecutedComponent component : execution.getComponents()) {
            Observation seen = observations.of(execution.getId(), component.getKey());
            components.add(new ExecutedComponentDTO(component.getKey(), component.getName(), component.getType(),
                    seen.status(), seen.response()));
        }
        return toDTO(execution, components);
    }

    @Mapping(target = "scenarioId", source = "execution.scenario.id")
    @Mapping(target = "components", source = "components")
    ExecutionDTO toDTO(Execution execution, List<ExecutedComponentDTO> components);
}
