package es.codeurjc.infracture.catalog.domain;

import java.util.Map;

public record ComponentConfiguration(String profile, Map<String, Integer> parameters) {
}
