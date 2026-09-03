package es.codeurjc.infracture.catalog.api;

import es.codeurjc.infracture.catalog.domain.ComponentType;

public record ComponentTemplateDTO(
        String key,
        String name,
        ComponentType type) {
}
