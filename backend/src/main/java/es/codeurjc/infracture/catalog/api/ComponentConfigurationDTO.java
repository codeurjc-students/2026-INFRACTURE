package es.codeurjc.infracture.catalog.api;

import java.util.Map;

public record ComponentConfigurationDTO(String profile, Map<String, Integer> parameters) {
}
