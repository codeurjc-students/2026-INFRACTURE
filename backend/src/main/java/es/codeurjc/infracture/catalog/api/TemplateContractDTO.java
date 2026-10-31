package es.codeurjc.infracture.catalog.api;

import java.util.List;

public record TemplateContractDTO(int version, List<ProfileDTO> profiles) {

    public record ProfileDTO(String name, List<ParameterDTO> parameters) {
    }

    public record ParameterDTO(String name, int min, int max) {
    }
}
