package es.codeurjc.infracture.project.api;

import es.codeurjc.infracture.project.domain.Scenario;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ScenarioMapper {

    ScenarioDTO toDTO(Scenario scenario);
}
