package es.codeurjc.infracture.catalog.api;

import es.codeurjc.infracture.catalog.domain.Capability;
import java.util.List;

public record TemplateContractDTO(int version, List<ProfileDTO> profiles) {

    public record ProfileDTO(String name, List<ParameterDTO> parameters, List<PortDTO> ports,
            List<Capability> capabilities) {
    }

    public record ParameterDTO(String name, int min, int max) {
    }

    public record PortDTO(String name, String label, List<Capability> accepts, int min, int max) {
    }
}
