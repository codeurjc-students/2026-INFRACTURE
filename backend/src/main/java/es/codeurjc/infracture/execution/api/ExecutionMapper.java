package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.application.ExecutionView;
import es.codeurjc.infracture.execution.application.ExecutionView.Node;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.Execution;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ExecutionMapper {

    // Joins the frozen nodes with what the backend last saw of each one.
    default ExecutionDTO toDTO(ExecutionView view) {
        List<ExecutedComponentDTO> components = new ArrayList<>();
        for (ExecutedComponent component : view.execution().getComponents()) {
            Node seen = view.node(component.getKey());
            components.add(new ExecutedComponentDTO(component.getKey(), component.getName(), component.getType(),
                    seen.status(), seen.response()));
        }
        return toDTO(view.execution(), components, view.secondsLeft());
    }

    @Mapping(target = "scenarioId", source = "execution.scenario.id")
    @Mapping(target = "components", source = "components")
    @Mapping(target = "secondsLeft", source = "secondsLeft")
    ExecutionDTO toDTO(Execution execution, List<ExecutedComponentDTO> components, Long secondsLeft);
}
