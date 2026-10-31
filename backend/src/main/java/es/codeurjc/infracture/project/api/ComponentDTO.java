package es.codeurjc.infracture.project.api;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import java.util.Map;
import java.util.UUID;

public record ComponentDTO(
        UUID key,
        String templateKey,
        ComponentType type,
        String name,
        PositionDTO position,
        String profile,
        Map<String, Integer> parameters) {
}
