package es.codeurjc.infracture.catalog.domain;

import java.util.List;

public record ProfileContract(String name, List<ParameterContract> parameters) {
}
