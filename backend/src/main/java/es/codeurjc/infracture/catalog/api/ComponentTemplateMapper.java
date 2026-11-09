package es.codeurjc.infracture.catalog.api;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import es.codeurjc.infracture.catalog.application.ContractCatalog;
import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ParameterContract;
import es.codeurjc.infracture.catalog.domain.TemplateContract;
import es.codeurjc.infracture.catalog.domain.WorkloadContract;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ComponentTemplateMapper {

    default ComponentTemplateDTO toDTO(ComponentTemplate template, ContractCatalog contracts) {
        return new ComponentTemplateDTO(template.getKey(), template.getName(), template.getType(),
                contracts.find(template.getType()).map(this::toDTO).orElse(null),
                toDTO(template.getInitialConfiguration()));
    }

    TemplateContractDTO toDTO(TemplateContract contract);

    ComponentConfigurationDTO toDTO(ComponentConfiguration configuration);

    @Mapping(target = "name", source = "profile")
    @Mapping(target = "seed", expression = "java(toDTO(WorkloadContract.SEED))")
    WorkloadProfileDTO toDTO(WorkloadContract contract);

    TemplateContractDTO.ParameterDTO toDTO(ParameterContract parameter);

}
