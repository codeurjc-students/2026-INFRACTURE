package es.codeurjc.infracture.catalog;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ComponentTemplateMapper {

    ComponentTemplateDTO toDTO(ComponentTemplate template);

    List<ComponentTemplateDTO> toDTOs(
            List<ComponentTemplate> templates);

}
