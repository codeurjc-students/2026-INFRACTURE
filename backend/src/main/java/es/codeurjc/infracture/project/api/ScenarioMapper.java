package es.codeurjc.infracture.project.api;

import es.codeurjc.infracture.project.domain.Component;
import es.codeurjc.infracture.project.domain.ComponentConfiguration;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Scenario;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ScenarioMapper {

    ScenarioDTO toDTO(Scenario scenario);

    ScenarioGraphDTO toGraphDTO(Scenario scenario);

    default ComponentDTO toDTO(Component component) {
        ComponentConfiguration configuration = component.getConfiguration();
        return new ComponentDTO(component.getKey(), component.getTemplateKey(), component.getType(),
                component.getContractVersion(), component.getName(),
                new PositionDTO(component.getPositionX(), component.getPositionY()),
                configuration.profile(), configuration.parameters());
    }

    default ComponentDraft toDraft(SaveComponentDTO component) {
        return new ComponentDraft(component.key(), component.templateKey(), component.name(),
                component.position().x(), component.position().y(),
                new ComponentConfiguration(component.profile(), component.parameters()));
    }
}
