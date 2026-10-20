package es.codeurjc.infracture.project.api;

import es.codeurjc.infracture.project.domain.Project;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectMapper {

    ProjectDTO toDTO(Project project);
}
