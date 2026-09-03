package es.codeurjc.infracture.catalog.api;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import es.codeurjc.infracture.catalog.domain.ComponentTemplate;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ComponentTemplateMapper {

    ComponentTemplateDTO toDTO(ComponentTemplate template);

    List<ComponentTemplateDTO> toDTOs(
            List<ComponentTemplate> templates);

}
