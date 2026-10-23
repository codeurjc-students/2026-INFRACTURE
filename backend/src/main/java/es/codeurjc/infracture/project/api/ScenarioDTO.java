package es.codeurjc.infracture.project.api;

import java.time.Instant;

public record ScenarioDTO(
        Long id,
        String name,
        String description,
        int revision,
        Instant createdAt,
        Instant updatedAt) {
}
