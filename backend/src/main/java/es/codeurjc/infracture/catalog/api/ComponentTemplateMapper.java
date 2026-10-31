package es.codeurjc.infracture.catalog.api;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import es.codeurjc.infracture.catalog.application.ContractCatalog;
import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.TemplateContract;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ComponentTemplateMapper {

    default ComponentTemplateDTO toDTO(ComponentTemplate template, ContractCatalog contracts) {
        return new ComponentTemplateDTO(template.getKey(), template.getName(), template.getType(),
                contracts.find(template.getType()).map(this::toDTO).orElse(null),
                toDTO(template.getInitialConfiguration()));
    }

    TemplateContractDTO toDTO(TemplateContract contract);

    ComponentConfigurationDTO toDTO(ComponentConfiguration configuration);

}
