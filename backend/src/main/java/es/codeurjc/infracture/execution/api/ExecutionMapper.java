package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.domain.Execution;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ExecutionMapper {

    @Mapping(target = "scenarioId", source = "scenario.id")
    ExecutionDTO toDTO(Execution execution);
}
