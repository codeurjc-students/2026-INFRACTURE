package es.codeurjc.infracture.project.api;

import java.time.Instant;

public record ProjectDTO(
        Long id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt) {
}
