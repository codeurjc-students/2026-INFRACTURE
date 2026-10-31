package es.codeurjc.infracture.project.api;

import es.codeurjc.infracture.project.application.ScenarioGraph;
import es.codeurjc.infracture.project.domain.Component;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.ValidationIssue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ScenarioMapper {

    ScenarioDTO toDTO(Scenario scenario);

    ValidationIssueDTO toDTO(ValidationIssue issue);

    @Mapping(target = "revision", source = "scenario.revision")
    @Mapping(target = "components", source = "scenario.components")
    ScenarioGraphDTO toDTO(ScenarioGraph graph);

    @Mapping(target = "position.x", source = "positionX")
    @Mapping(target = "position.y", source = "positionY")
    @Mapping(target = "profile", source = "configuration.profile")
    @Mapping(target = "parameters", source = "configuration.parameters")
    ComponentDTO toDTO(Component component);

    @Mapping(target = "positionX", source = "position.x")
    @Mapping(target = "positionY", source = "position.y")
    @Mapping(target = "configuration.profile", source = "profile")
    @Mapping(target = "configuration.parameters", source = "parameters")
    ComponentDraft toDraft(SaveComponentDTO component);
}
