package es.codeurjc.infracture.catalog.api;

import es.codeurjc.infracture.catalog.api.TemplateContractDTO.ParameterDTO;
import es.codeurjc.infracture.catalog.domain.Capability;
import java.util.List;
import java.util.Map;

// A load profile: its integer parameters with limits, the seed's range, the capabilities its target must
// offer and the values a new load starts with.
public record WorkloadProfileDTO(String name, List<ParameterDTO> parameters, ParameterDTO seed,
        List<Capability> requires, Map<String, Integer> initialParameters) {
}
